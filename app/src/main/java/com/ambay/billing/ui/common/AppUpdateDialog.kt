package com.ambay.billing.ui.common

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ambay.billing.BuildConfig
import com.ambay.billing.data.remote.AppReleaseInfo
import com.ambay.billing.data.remote.GitHubUpdateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppUpdateDialog(
    updateClient: GitHubUpdateClient,
    initialRelease: AppReleaseInfo?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf(initialRelease) }
    var checking by remember { mutableStateOf(initialRelease == null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (release == null) {
            checking = true
            val res = withContext(Dispatchers.IO) { runCatching { updateClient.checkLatestRelease() }.getOrNull() }
            release = res
            checking = false
        }
    }

    val rel = release
    val updateAvailable = rel != null && GitHubUpdateClient.isUpdateAvailable(rel.tagName, BuildConfig.VERSION_CODE)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D2248),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.9f),
        title = { Text("App Update") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    checking -> Text("Naya update check ho raha hai...")
                    errorMsg != null -> Text("Error: $errorMsg", color = Color(0xFFFCA5A5))
                    rel == null -> Text("Update info nahi mil paya. Internet check karo.")
                    !updateAvailable -> Text("Aapke paas latest version hai (${BuildConfig.VERSION_NAME}).")
                    else -> {
                        Text("Naya version ${rel.tagName} available hai!", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(rel.releaseNotes.take(400), style = MaterialTheme.typography.bodyMedium)
                        if (downloading) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text("Download ho raha hai: ${(progress * 100).toInt()}%")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (updateAvailable && !downloading && rel != null) {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            return@TextButton
                        }
                        downloading = true
                        errorMsg = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val apk = updateClient.downloadAndVerifyApk(rel) { p -> progress = p }
                                    updateClient.buildInstallIntent(apk)
                                }
                            }
                            downloading = false
                            result.onSuccess { intent -> context.startActivity(intent) }
                                .onFailure { err -> errorMsg = err.message }
                        }
                    }
                ) {
                    Text("Update Karo", color = Color(0xFF00B8D9))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Band Karo", color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}
