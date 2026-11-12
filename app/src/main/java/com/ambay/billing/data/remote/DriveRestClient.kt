package com.ambay.billing.data.remote

import com.ambay.billing.core.TrustCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class DriveFileItem(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: String
)

data class DriveFolders(
    val root: String,
    val bills: String,
    val inv: String,
    val snaps: String,
    val man: String,
    val keys: String
)

/**
 * Google Drive REST v3 client (no WebView).
 * Mirrors js/sync.js layout: AMBAY_MOBILE_ACCESSORIES_BACKUP/{Bills,Inventory,Snapshots,Manifest,Keys}.
 */
@Singleton
class DriveRestClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val ROOT_FOLDER = "AMBAY_MOBILE_ACCESSORIES_BACKUP"
        private const val API = "https://www.googleapis.com"
        private val SNAP_RE = Regex("^snapshot_(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})Z_[0-9a-f]{8}\\.json$")
        private const val DAY_MS = 86_400_000L
    }

    private fun reqJson(token: String, url: String, method: String = "GET", body: String? = null, contentType: String = "application/json"): JsonObject {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
        if (method == "POST") {
            builder.post((body ?: "{}").toRequestBody(contentType.toMediaType()))
        } else if (method == "PATCH") {
            builder.patch((body ?: "{}").toRequestBody(contentType.toMediaType()))
        } else {
            builder.get()
        }
        val resp = httpClient.newCall(builder.build()).execute()
        val respText = resp.body?.string().orEmpty()
        if (resp.code == 401) throw IllegalStateException("Drive token expired (401)")
        if (!resp.isSuccessful) throw IllegalStateException("Drive HTTP ${resp.code}: $respText")
        return json.parseToJsonElement(respText).jsonObject
    }

    fun ensureFolders(token: String): DriveFolders {
        val root = findFolder(token, ROOT_FOLDER, null) ?: mkFolder(token, ROOT_FOLDER, null)
        val bills = findFolder(token, "Bills", root) ?: mkFolder(token, "Bills", root)
        val inv = findFolder(token, "Inventory", root) ?: mkFolder(token, "Inventory", root)
        val snaps = findFolder(token, "Snapshots", root) ?: mkFolder(token, "Snapshots", root)
        val man = findFolder(token, "Manifest", root) ?: mkFolder(token, "Manifest", root)
        val keys = findFolder(token, "Keys", root) ?: mkFolder(token, "Keys", root)
        return DriveFolders(root, bills, inv, snaps, man, keys)
    }

    private fun findFolder(token: String, name: String, parentId: String?): String? {
        val q = "mimeType='application/vnd.google-apps.folder' and name='${name.replace("'", "\\'")}' and trashed=false" +
            if (parentId != null) " and '$parentId' in parents" else ""
        val url = "$API/drive/v3/files?q=${URLEncoder.encode(q, "UTF-8")}&fields=files(id,name)&pageSize=10"
        val res = reqJson(token, url)
        return res["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    private fun mkFolder(token: String, name: String, parentId: String?): String {
        val parentsJson = if (parentId != null) ""","parents":["$parentId"]""" else ""
        val body = """{"name":"$name","mimeType":"application/vnd.google-apps.folder"$parentsJson}"""
        val res = reqJson(token, "$API/drive/v3/files?fields=id", "POST", body)
        return res["id"]?.jsonPrimitive?.contentOrNull ?: error("Failed to create folder $name")
    }

    fun uploadSnapshot(token: String, contentJson: String, keepCount: Int = 7): DriveFileItem {
        val folders = ensureFolders(token)
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val tsStr = fmt.format(Date())
        val hex8 = ByteArray(4).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val name = "snapshot_${tsStr}_$hex8.json"
        val uploaded = multipartUpload(token, name, folders.snaps, contentJson)
        runCatching { pruneSnapshots(token, folders.snaps, uploaded.id, keepCount) }
        return uploaded
    }

    fun multipartUpload(token: String, name: String, parentId: String, content: String): DriveFileItem {
        val boundary = "ambay_${TrustCrypto.randomB64u(8)}"
        val metaJson = """{"name":"$name","parents":["$parentId"],"mimeType":"application/json"}"""
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metaJson\r\n" +
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$content\r\n--$boundary--"
        val res = reqJson(
            token,
            "$API/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,modifiedTime",
            "POST",
            body,
            "multipart/related; boundary=$boundary"
        )
        val id = res["id"]?.jsonPrimitive?.contentOrNull ?: error("Drive upload missing id")
        val size = res["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val expectedBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
        require(size == expectedBytes) { "Uploaded size mismatch: $size != $expectedBytes" }
        return DriveFileItem(
            id = id,
            name = res["name"]?.jsonPrimitive?.contentOrNull ?: name,
            size = size,
            modifiedTime = res["modifiedTime"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }

    fun listFiles(token: String, parentId: String): List<DriveFileItem> {
        val q = "'$parentId' in parents and trashed=false"
        val url = "$API/drive/v3/files?q=${URLEncoder.encode(q, "UTF-8")}&fields=files(id,name,size,modifiedTime)&pageSize=200&orderBy=modifiedTime%20desc"
        val res = reqJson(token, url)
        return res["files"]?.jsonArray?.map { el ->
            val o = el.jsonObject
            DriveFileItem(
                id = o["id"]?.jsonPrimitive?.content.orEmpty(),
                name = o["name"]?.jsonPrimitive?.content.orEmpty(),
                size = o["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                modifiedTime = o["modifiedTime"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }.orEmpty()
    }

    fun downloadText(token: String, fileId: String): String {
        val req = Request.Builder()
            .url("$API/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw IllegalStateException("Download failed: HTTP ${resp.code}")
        val txt = resp.body?.string().orEmpty()
        require(txt.length <= 25_000_000) { "Backup file too large" }
        return txt
    }

    private fun pruneSnapshots(token: String, snapsFolderId: String, verifiedId: String, keep: Int) {
        if (keep <= 0) return
        val files = listFiles(token, snapsFolderId)
        if (files.none { it.id == verifiedId }) return
        val parsed = files.mapNotNull { f ->
            val m = SNAP_RE.matchEntire(f.name) ?: return@mapNotNull null
            f to m.groupValues[1].toInt() // quick sort by timestamp via name
        }.sortedByDescending { it.first.name }
        if (parsed.size <= keep) return
        val keepIds = parsed.take(keep).map { it.first.id }.toMutableSet()
        keepIds += verifiedId
        for ((f, _) in parsed) {
            if (f.id !in keepIds) {
                runCatching {
                    reqJson(token, "$API/drive/v3/files/${f.id}?fields=id", "PATCH", """{"trashed":true}""")
                }
            }
        }
    }
}
