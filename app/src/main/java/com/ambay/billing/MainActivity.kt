package com.ambay.billing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ambay.billing.core.auth.AuthManager
import com.ambay.billing.core.auth.BiometricHelper
import com.ambay.billing.core.auth.GoogleRecoveryManager
import com.ambay.billing.core.auth.UserRole
import com.ambay.billing.core.crypto.SecurityAudit
import com.ambay.billing.core.print.BluetoothPrintManager
import com.ambay.billing.core.scan.BeepManager
import com.ambay.billing.core.sync.AmbayRepository
import com.ambay.billing.ui.screens.ForgotPasswordDialog
import com.ambay.billing.ui.screens.LoginScreen
import com.ambay.billing.ui.screens.OwnerWebViewScreen
import com.ambay.billing.ui.screens.SetupWizardScreen
import com.ambay.billing.ui.screens.StaffMainScreen
import com.ambay.billing.ui.theme.AmbayBillingTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var repository: AmbayRepository

    @Inject
    lateinit var beepManager: BeepManager

    @Inject
    lateinit var bluetoothPrintManager: BluetoothPrintManager

    @Inject
    lateinit var googleRecoveryManager: GoogleRecoveryManager

    private var backgroundTimestamp = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Request runtime permissions
        checkAndRequestPermissions()

        // Perform security check
        val security = SecurityAudit.checkSecurity()
        if (security.isRooted || security.isDebuggerConnected) {
            // Log security warning
        }

        // Setup background auto-lock observer (2 minutes default)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundTimestamp = System.currentTimeMillis()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (backgroundTimestamp > 0L) {
                    val elapsed = System.currentTimeMillis() - backgroundTimestamp
                    val timeoutMs = authManager.getAutoLockTimeoutMinutes() * 60 * 1000L
                    if (elapsed >= timeoutMs) {
                        authManager.lock()
                    }
                    backgroundTimestamp = 0L
                }
            }
        })

        setContent {
            AmbayBillingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun updateFlagSecure(isSecure: Boolean) {
        if (isSecure) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Composable
    private fun AppNavigation() {
        val isLocked by authManager.isLocked.collectAsState()
        val currentRole by authManager.currentRole.collectAsState()
        val scope = rememberCoroutineScope()

        var isSetupCompleted by remember { mutableStateOf(authManager.isSetupCompleted()) }
        var forgotPasswordRole by remember { mutableStateOf<UserRole?>(null) }
        var scanCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

        // Manage FLAG_SECURE dynamically on password screens
        DisposableEffect(isLocked, isSetupCompleted) {
            updateFlagSecure(isLocked || !isSetupCompleted)
            onDispose {
                updateFlagSecure(false)
            }
        }

        AnimatedContent(
            targetState = Triple(isSetupCompleted, isLocked, currentRole),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "root_navigation"
        ) { (setupDone, locked, role) ->
            if (!setupDone) {
                SetupWizardScreen(
                    authManager = authManager,
                    repository = repository,
                    onSetupCompleted = {
                        isSetupCompleted = true
                    },
                    onGoogleSignInClick = { onResult ->
                        scope.launch {
                            val email = googleRecoveryManager.signInWithGoogle()
                            if (email != null) {
                                onResult(email)
                            } else {
                                Toast.makeText(this@MainActivity, "Google Sign-In cancel hua", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onScanQrClick = { onScanned ->
                        scanCallback = onScanned
                    }
                )
            } else if (locked) {
                LoginScreen(
                    authManager = authManager,
                    onLoginSuccess = { loggedInRole ->
                        // AuthManager handles unlocking
                    },
                    onBiometricClick = { selectedRole ->
                        BiometricHelper.promptBiometric(
                            activity = this@MainActivity,
                            title = "AMBAY Billing Unlock",
                            subtitle = "Apne fingerprint ya face se unlock karein",
                            negativeButtonText = "Password use karein",
                            onSuccess = {
                                if (!authManager.unlockWithBiometrics(selectedRole)) {
                                    Toast.makeText(this@MainActivity, "Owner ke liye password zaroori hai", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onError = { err ->
                                Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    onForgotPasswordClick = { roleToReset ->
                        forgotPasswordRole = roleToReset
                    }
                )
            } else {
                when (role) {
                    UserRole.STAFF -> {
                        StaffMainScreen(
                            repository = repository,
                            beepManager = beepManager,
                            bluetoothPrintManager = bluetoothPrintManager,
                            authManager = authManager,
                            onLockRequested = {
                                authManager.lock()
                            }
                        )
                    }
                    UserRole.OWNER -> {
                        OwnerWebViewScreen(
                            authManager = authManager,
                            bluetoothPrintManager = bluetoothPrintManager,
                            onSwitchToStaff = {
                                authManager.switchRole(UserRole.STAFF)
                            },
                            onLockRequested = {
                                authManager.lock()
                            },
                            onNativeScanRequested = { callback ->
                                scanCallback = callback
                            }
                        )
                    }
                    else -> {
                        // Default fallback to Login
                        authManager.lock()
                    }
                }
            }
        }

        scanCallback?.let { cb ->
            com.ambay.billing.ui.components.ScanDialog(
                onResult = { code -> scanCallback = null; cb(code) },
                onDismiss = { scanCallback = null }
            )
        }

        // Forgot Password Dialog
        if (forgotPasswordRole != null) {
            ForgotPasswordDialog(
                role = forgotPasswordRole!!,
                authManager = authManager,
                onDismiss = { forgotPasswordRole = null },
                onGoogleSignInRequest = { onEmailReceived ->
                    scope.launch {
                        val email = googleRecoveryManager.signInWithGoogle()
                        if (email != null) {
                            onEmailReceived(email)
                        } else {
                            Toast.makeText(this@MainActivity, "Google Sign-In cancel hua ya account select nahi hua", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        beepManager.release()
    }
}
