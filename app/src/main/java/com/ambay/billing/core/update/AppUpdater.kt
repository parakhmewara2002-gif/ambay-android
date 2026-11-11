package com.ambay.billing.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ambay.billing.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/**
 * Self-update from GitHub Releases (repo must be public). Release tag format: v1.0.<versionCode>.
 * Uses its own plain OkHttpClient (NOT the signed-registry client).
 */
object AppUpdater {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    /** Returns UpdateInfo if a newer release exists, null if up to date. Throws on network/parse errors. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Update server HTTP ${r.code}")
            val o = JSONObject(r.body?.string() ?: throw IllegalStateException("empty response"))
            val tag = o.getString("tag_name")
            val code = tag.substringAfterLast('.').toIntOrNull() ?: throw IllegalStateException("bad tag $tag")
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = o.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
            }
            UpdateInfo(code, tag.removePrefix("v"), url ?: throw IllegalStateException("release me APK nahi hai"), o.optString("body", ""))
        }
    }

    /** Downloads the APK into cache and returns the file. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "ambay-${info.versionCode}.apk")
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Download HTTP ${r.code}")
            val body = r.body ?: throw IllegalStateException("empty download")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(32 * 1024); var done = 0L; var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        os.write(buf, 0, n); done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        }
        out
    }

    /** Opens the system installer. If "install unknown apps" is off, opens that setting instead and returns false. */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
