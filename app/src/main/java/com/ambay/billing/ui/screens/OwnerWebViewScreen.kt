package com.ambay.billing.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ambay.billing.BuildConfig
import com.ambay.billing.core.auth.AuthManager
import com.ambay.billing.core.print.BluetoothPrintManager
import com.ambay.billing.core.print.EscPosBuilder
import com.ambay.billing.ui.components.AmbayAppBar
import com.ambay.billing.ui.theme.BrandBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OwnerWebViewScreen(
    authManager: AuthManager,
    bluetoothPrintManager: BluetoothPrintManager,
    onSwitchToStaff: () -> Unit,
    onLockRequested: () -> Unit,
    onNativeScanRequested: (callback: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var showStaffPasswordDialog by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }

    // File chooser launcher
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intentData = result.data
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, intentData)
            fileUploadCallback?.onReceiveValue(uris)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    // Hardware Back Button
    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    Scaffold(
        topBar = {
            AmbayAppBar(
                title = "AMBAY Billing",
                subtitle = "Owner",
                syncStatus = "synced",
                actions = {
                    // Switch to Staff View
                    IconButton(onClick = onSwitchToStaff) {
                        Icon(imageVector = Icons.Default.SwitchAccount, contentDescription = "Staff View", tint = Color.White)
                    }
                    // Change Staff Password
                    IconButton(onClick = { showStaffPasswordDialog = true }) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "Staff Password", tint = Color.White)
                    }
                    // Reload web page
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                    }
                    // Lock
                    IconButton(onClick = onLockRequested) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = Color.White)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this

                        // Hardened Security Settings
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }

                        // Native Javascript Bridge
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun scan() {
                                    if (!isOwnSite(CurrentUrl.value)) return
                                    (ctx as? Activity)?.runOnUiThread {
                                        onNativeScanRequested { barcode ->
                                            val q = jsQuote(barcode)
                                            val js = "if (window.__onNativeScan) { window.__onNativeScan($q); } else if (window.onBarcodeScanned) { window.onBarcodeScanned($q); }"
                                            evaluateJavascript(js, null)
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun printReceipt(content: String) {
                                    if (!isOwnSite(CurrentUrl.value)) return
                                    scope.launch(Dispatchers.IO) {
                                        val savedMac = bluetoothPrintManager.getSavedPrinterMac()
                                        if (savedMac != null) {
                                            val is80mm = bluetoothPrintManager.is80mmPrinter()
                                            val builder = EscPosBuilder(is80mm)
                                            builder.reset().line(content).feedAndCut()
                                            bluetoothPrintManager.printBytes(savedMac, builder.build())
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun saveFile(fileName: String, base64Content: String) {
                                    if (!isOwnSite(CurrentUrl.value)) return
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val cleanBase64 = base64Content.substringAfter("base64,")
                                            val bytes = Base64.getDecoder().decode(cleanBase64)
                                            saveFileToDownloads(ctx, fileName, bytes)
                                            (ctx as? Activity)?.runOnUiThread {
                                                Toast.makeText(ctx, "File saved: $fileName", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            (ctx as? Activity)?.runOnUiThread {
                                                Toast.makeText(ctx, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun share(title: String, text: String, url: String) {
                                    if (!isOwnSite(CurrentUrl.value)) return
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, title)
                                        putExtra(Intent.EXTRA_TEXT, "$text\n$url".trim())
                                    }
                                    ctx.startActivity(Intent.createChooser(intent, title))
                                }
                            },
                            "Android"
                        )

                        // Strict URL filtering: allow only official origins
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                CurrentUrl.value = url
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return true
                                return if (isAllowedOrigin(url)) {
                                    false
                                } else {
                                    // Open external links in external browser
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (ignored: Exception) {}
                                    true
                                }
                            }
                        }

                        // Handle permissions and file upload chooser
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                if (request != null && isOwnSite(request.origin?.toString())) {
                                    request.grant(request.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }.toTypedArray())
                                } else request?.deny()
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = filePathCallback
                                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "*/*"
                                }
                                try {
                                    fileChooserLauncher.launch(intent)
                                } catch (e: Exception) {
                                    fileUploadCallback = null
                                    return false
                                }
                                return true
                            }
                        }

                        loadUrl(BuildConfig.WEBVIEW_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Change Staff Password Dialog (Owner Extra)
        if (showStaffPasswordDialog) {
            var newStaffPass by remember { mutableStateOf("") }
            var confirmStaffPass by remember { mutableStateOf("") }
            var errorMsg by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { showStaffPasswordDialog = false },
                title = { Text("Staff Password Badlein", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Dukaan ke staff ke liye naya password set karein:")
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = newStaffPass,
                            onValueChange = { newStaffPass = it },
                            label = { Text("Naya Staff Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = confirmStaffPass,
                            onValueChange = { confirmStaffPass = it },
                            label = { Text("Confirm Staff Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (errorMsg != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = errorMsg ?: "", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newStaffPass.length < 6) {
                                errorMsg = "Kam se kam 6 characters chahiye"
                            } else if (newStaffPass != confirmStaffPass) {
                                errorMsg = "Passwords match nahi hue"
                            } else {
                                authManager.setStaffPassword(newStaffPass)
                                showStaffPasswordDialog = false
                                Toast.makeText(context, "Staff password update ho gaya", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Update Karo")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStaffPasswordDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private object CurrentUrl { @Volatile var value: String? = null }

private val ALLOWED_HOSTS = setOf("ambay-billing.pages.dev", "accounts.google.com", "cdnjs.cloudflare.com")

private fun isAllowedOrigin(url: String): Boolean {
    val u = try { Uri.parse(url) } catch (e: Exception) { return false }
    if (u.scheme != "https") return false
    val h = (u.host ?: return false).lowercase()
    return h in ALLOWED_HOSTS || h.endsWith(".googleapis.com")
}

private fun isOwnSite(url: String?): Boolean {
    val u = try { Uri.parse(url ?: return false) } catch (e: Exception) { return false }
    return u.scheme == "https" && u.host?.lowercase() == "ambay-billing.pages.dev"
}

private fun jsQuote(s: String): String = org.json.JSONObject.quote(s)

private fun saveFileToDownloads(context: Context, fileName: String, bytes: ByteArray) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(bytes)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { os ->
            os.write(bytes)
        }
    }
}

private fun getMimeType(fileName: String): String {
    return when {
        fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        fileName.endsWith(".json", ignoreCase = true) -> "application/json"
        fileName.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else -> "application/octet-stream"
    }
}
