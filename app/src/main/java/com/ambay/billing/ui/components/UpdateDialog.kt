package com.ambay.billing.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ambay.billing.BuildConfig
import com.ambay.billing.core.update.AppUpdater
import com.ambay.billing.core.update.UpdateInfo
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Check ho raha hai...") }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        try {
            val u = AppUpdater.check()
            if (u == null) status = "App up to date hai (v${BuildConfig.VERSION_NAME})"
            else { info = u; status = "Nayi update mili: v${u.versionName}" }
        } catch (e: Exception) {
            status = "Check nahi ho paya: ${e.message}"
        }
        busy = false
    }

    AlertDialog(
        onDismissRequest = { if (progress < 0) onDismiss() },
        title = { Text("App Update") },
        text = {
            Column {
                Text(status)
                info?.notes?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(8.dp)); Text(it.take(400)) }
                if (busy || progress >= 0) {
                    Spacer(Modifier.height(12.dp))
                    if (progress in 0..99) LinearProgressIndicator(progress = { progress / 100f }) else LinearProgressIndicator()
                }
            }
        },
        confirmButton = {
            val u = info
            if (u != null && progress < 0) {
                Button(onClick = {
                    progress = 0; busy = false
                    scope.launch {
                        try {
                            val f = AppUpdater.download(ctx, u) { progress = it }
                            status = if (AppUpdater.install(ctx, f)) "Installer khul gaya" else "Pehle 'Install unknown apps' allow karo, phir dobara Update dabao"
                        } catch (e: Exception) { status = "Download fail: ${e.message}" }
                        progress = -1
                    }
                }) { Text("Update Karo") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = progress < 0) { Text("Band karo") } }
    )
}
