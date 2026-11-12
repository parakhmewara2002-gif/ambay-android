package com.ambay.billing.data.remote

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ambay.billing.BuildConfig
import com.ambay.billing.core.TrustCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class AppReleaseInfo(
    val tagName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val apkUrl: String,
    val expectedSha256: String?
)

@Singleton
class GitHubUpdateClient @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("plain") private val plainClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val TAG_VERSION_RE = Regex("v1\\.0\\.(\\d+)")
        private val SHA256_RE = Regex("SHA-256:\\s*`?([0-9a-fA-F]{64})`?")

        fun parseVersionCodeFromTag(tag: String): Int? =
            TAG_VERSION_RE.find(tag.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()

        fun isUpdateAvailable(latestTag: String, currentVersionCode: Int): Boolean {
            val remoteCode = parseVersionCodeFromTag(latestTag) ?: return false
            return remoteCode > currentVersionCode
        }

        fun extractSha256(body: String): String? =
            SHA256_RE.find(body)?.groupValues?.getOrNull(1)?.lowercase()
    }

    fun checkLatestRelease(repo: String = BuildConfig.UPDATE_REPO): AppReleaseInfo? {
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AmbayBilling-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        val resp = plainClient.newCall(req).execute()
        if (!resp.isSuccessful) return null
        val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
        val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val vCode = parseVersionCodeFromTag(tag) ?: return null
        val body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val apkAsset = obj["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            ?: return null
        val apkUrl = apkAsset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        return AppReleaseInfo(
            tagName = tag,
            versionCode = vCode,
            releaseNotes = body,
            apkUrl = apkUrl,
            expectedSha256 = extractSha256(body)
        )
    }

    fun downloadAndVerifyApk(info: AppReleaseInfo, onProgress: (Float) -> Unit): File {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val outFile = File(updatesDir, "ambay-${info.tagName}.apk")
        val req = Request.Builder().url(info.apkUrl).get().build()
        val resp = plainClient.newCall(req).execute()
        if (!resp.isSuccessful) error("Download failed: HTTP ${resp.code}")
        val body = resp.body ?: error("Empty APK body")
        val total = body.contentLength()
        val digest = MessageDigest.getInstance("SHA-256")

        body.byteStream().use { input ->
            outFile.outputStream().use { output ->
                val buf = ByteArray(8192)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    output.write(buf, 0, read)
                    digest.update(buf, 0, read)
                    downloaded += read
                    if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (info.expectedSha256 != null && !actualSha.equals(info.expectedSha256, ignoreCase = true)) {
            outFile.delete()
            error("APK SHA-256 verification failed! Expected ${info.expectedSha256}, got $actualSha")
        }
        onProgress(1f)
        return outFile
    }

    fun buildInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
