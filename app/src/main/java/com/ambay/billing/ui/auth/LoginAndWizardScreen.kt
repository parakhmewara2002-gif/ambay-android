package com.ambay.billing.ui.auth

import android.app.Activity
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ambay.billing.core.UserRole
import com.ambay.billing.hardware.BarcodeScannerDialog
import com.ambay.billing.ui.theme.BrandCyan
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandSuccess
import com.ambay.billing.ui.theme.GlassButton
import com.ambay.billing.ui.theme.GlassCard
import dev.chrisbanes.haze.HazeState

@Composable
fun FlagSecureEffect() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = BrandCyan,
    unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
    focusedLabelColor = BrandCyan,
    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
    cursorColor = BrandCyan
)

@Composable
fun FirstLaunchWizardScreen(
    ui: AuthUiState,
    hazeState: HazeState,
    vm: AuthViewModel
) {
    FlagSecureEffect()
    val context = LocalContext.current
    var showQrScanner by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "AMBAY MOBILE ACCESSORIES",
            style = MaterialTheme.typography.labelLarge,
            color = BrandCyan
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "First-Time Setup (${ui.wizardStep + 1}/4)",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))

        GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(targetState = ui.wizardStep, label = "wizard_step") { step ->
                when (step) {
                    0 -> {
                        var pwd by remember { mutableStateOf("") }
                        var confirm by remember { mutableStateOf("") }
                        Column {
                            Text("Step 1: Owner Password Set Karo", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("Kam se kam 6 characters. Owner mode me biometric nahi chalega (shared phone safety).", color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pwd,
                                onValueChange = { pwd = it },
                                label = { Text("Owner Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirm,
                                onValueChange = { confirm = it },
                                label = { Text("Confirm Owner Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(16.dp))
                            GlassButton("Aage Badho", onClick = { vm.saveWizardOwnerPassword(pwd, confirm) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    1 -> {
                        var gmail by remember { mutableStateOf("") }
                        Column {
                            Text("Step 2: Recovery Gmail Verify Karo", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("Password bhoolne par Google Sign-In se recovery hogi.", color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = gmail,
                                onValueChange = { gmail = it },
                                label = { Text("Owner Gmail Address") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(16.dp))
                            GlassButton(
                                text = if (ui.isBusy) "Verify ho raha hai..." else "Google Sign-In se Verify Karo",
                                enabled = !ui.isBusy,
                                onClick = {
                                    vm.verifyGoogleAccount(context, gmail) { verified ->
                                        vm.saveWizardRecoveryGmail(verified)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    2 -> {
                        var pwd by remember { mutableStateOf("") }
                        var confirm by remember { mutableStateOf("") }
                        Column {
                            Text("Step 3: Staff Password Set Karo", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("Staff ke liye alag password (kam se kam 6 characters).", color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pwd,
                                onValueChange = { pwd = it },
                                label = { Text("Staff Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirm,
                                onValueChange = { confirm = it },
                                label = { Text("Confirm Staff Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(16.dp))
                            GlassButton("Aage Badho", onClick = { vm.saveWizardStaffPassword(pwd, confirm) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    else -> {
                        var pastedQr by remember { mutableStateOf("") }
                        Column {
                            Text("Step 4: Website Server se Pair Karo", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("Website > More > Linked Devices me QR banakar yahan scan karo ya paste karo.", color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.height(12.dp))
                            GlassButton(
                                text = "QR Scan Karo",
                                icon = Icons.Rounded.QrCodeScanner,
                                onClick = { showQrScanner = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pastedQr,
                                onValueChange = { pastedQr = it },
                                label = { Text("Ya AMBPAIR1... text paste karo") },
                                colors = glassTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                GlassButton(
                                    text = "Pair Karo",
                                    enabled = pastedQr.isNotBlank() && !ui.isBusy,
                                    onClick = { vm.pairWithQr(pastedQr) },
                                    modifier = Modifier.weight(1f)
                                )
                                GlassButton(
                                    text = "Baad Me",
                                    containerColor = Color.White.copy(alpha = 0.16f),
                                    contentColor = Color.White,
                                    onClick = { vm.skipPairingAndFinishWizard() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (ui.errorMsg != null) {
                Spacer(Modifier.height(12.dp))
                Text(ui.errorMsg, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showQrScanner) {
        BarcodeScannerDialog(
            title = "Pairing QR Scan Karo",
            onDismiss = { showQrScanner = false },
            onResult = { raw ->
                showQrScanner = false
                vm.pairWithQr(raw)
            }
        )
    }
}

@Composable
fun LoginScreen(
    ui: AuthUiState,
    hazeState: HazeState,
    vm: AuthViewModel
) {
    FlagSecureEffect()
    val context = LocalContext.current
    var showForgotDialog by remember { mutableStateOf<UserRole?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("AMBAY MOBILE ACCESSORIES", style = MaterialTheme.typography.labelLarge, color = BrandCyan)
        Spacer(Modifier.height(4.dp))
        Text("Kaun login kar raha hai?", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(20.dp))

        if (ui.deviceRevoked) {
            GlassCard(hazeState = hazeState, tintColor = BrandError.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth()) {
                Text("Is device ko owner ne logout kar diya", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            GlassCard(
                hazeState = hazeState,
                tintColor = if (ui.selectedRole == UserRole.OWNER) BrandCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .weight(1f)
                    .clickable { vm.selectRole(UserRole.OWNER) }
            ) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text("OWNER", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Full Billing, Stock & Reports", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
            }

            GlassCard(
                hazeState = hazeState,
                tintColor = if (ui.selectedRole == UserRole.STAFF) BrandCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .weight(1f)
                    .clickable { vm.selectRole(UserRole.STAFF) }
            ) {
                Icon(Icons.Rounded.Badge, contentDescription = null, tint = BrandSuccess, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text("STAFF", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Scan, Print, Search & View", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
            }
        }

        if (ui.selectedRole != null) {
            Spacer(Modifier.height(18.dp))
            var pwd by remember(ui.selectedRole) { mutableStateOf("") }
            GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${ui.selectedRole.name} Login",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("Password daalo") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                GlassButton(
                    text = "Unlock ${ui.selectedRole.name}",
                    onClick = { vm.loginWithPassword(ui.selectedRole, pwd) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Biometric strictly for STAFF only! Owner NEVER uses biometric.
                if (ui.selectedRole == UserRole.STAFF && ui.staffBiometricEnabled) {
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        text = "Fingerprint se kholo (Staff)",
                        icon = Icons.Rounded.Fingerprint,
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                        onClick = {
                            val activity = context as? FragmentActivity ?: return@GlassButton
                            val prompt = BiometricPrompt(
                                activity,
                                ContextCompat.getMainExecutor(context),
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        vm.onStaffBiometricSuccess()
                                    }
                                }
                            )
                            val info = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Staff Biometric Unlock")
                                .setSubtitle("Sirf Staff POS kholne ke liye")
                                .setNegativeButtonText("Password use karo")
                                .build()
                            prompt.authenticate(info)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showForgotDialog = ui.selectedRole }) {
                    Text("Password bhool gaye?", color = BrandCyan)
                }

                if (ui.errorMsg != null) {
                    Text(ui.errorMsg, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodyMedium)
                }
                if (ui.infoMsg != null) {
                    Text(ui.infoMsg, color = BrandSuccess, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    showForgotDialog?.let { targetRole ->
        var verified by remember { mutableStateOf(false) }
        var newPwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showForgotDialog = null },
            containerColor = Color(0xFF0D2248),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.9f),
            title = {
                Text(
                    if (targetRole == UserRole.OWNER) "Owner Password Reset"
                    else "Staff Password Reset (Owner Authorization)"
                )
            },
            text = {
                Column {
                    Text("Recovery Gmail: ${ui.maskedRecoveryGmail}")
                    Spacer(Modifier.height(8.dp))
                    if (!verified) {
                        Text(
                            if (targetRole == UserRole.STAFF)
                                "Staff apna password khud reset nahi kar sakta. Owner ko Google Sign-In se verify karna hoga."
                            else
                                "Apne recovery Gmail se Google Sign-In karke verify karo."
                        )
                        Spacer(Modifier.height(10.dp))
                        GlassButton(
                            text = "Owner Google Sign-In",
                            onClick = {
                                vm.verifyGoogleAccount(context, vm.getRecoveryGmailRaw()) {
                                    verified = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = newPwd,
                            onValueChange = { newPwd = it },
                            label = { Text("Naya ${targetRole.name} Password (min 6)") },
                            visualTransformation = PasswordVisualTransformation(),
                            colors = glassTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (ui.errorMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(ui.errorMsg, color = Color(0xFFFCA5A5))
                    }
                }
            },
            confirmButton = {
                if (verified) {
                    TextButton(onClick = {
                        vm.resetPasswordAfterGoogleVerify(targetRole, newPwd)
                        showForgotDialog = null
                    }) {
                        Text("Save Karo", color = BrandCyan)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}
