package com.ambay.billing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambay.billing.core.auth.AuthManager
import com.ambay.billing.core.sync.AmbayRepository
import com.ambay.billing.ui.theme.BrandAccent
import com.ambay.billing.ui.theme.BrandBlue
import com.ambay.billing.ui.theme.BrandError
import com.ambay.billing.ui.theme.BrandGradient
import com.ambay.billing.ui.theme.BrandSuccess
import kotlinx.coroutines.launch

@Composable
fun SetupWizardScreen(
    authManager: AuthManager,
    repository: AmbayRepository,
    onSetupCompleted: () -> Unit,
    onGoogleSignInClick: (onResult: (String) -> Unit) -> Unit,
    onScanQrClick: (onScanned: (String) -> Unit) -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: Owner Pass, 2: Recovery Gmail, 3: Staff Pass, 4: Pair Server
    val scope = rememberCoroutineScope()

    var ownerPassword by remember { mutableStateOf("") }
    var ownerPasswordConfirm by remember { mutableStateOf("") }

    var recoveryEmailInput by remember { mutableStateOf("") }
    var verifiedRecoveryEmail by remember { mutableStateOf<String?>(null) }

    var staffPassword by remember { mutableStateOf("") }
    var staffPasswordConfirm by remember { mutableStateOf("") }

    var pairQrText by remember { mutableStateOf("") }
    var isPairingLoading by remember { mutableStateOf(false) }
    var isPairSuccess by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandGradient)
                    .statusBarsPadding()
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "AMBAY Billing Setup Wizard",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kadam $step / 4",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                when (step) {
                    1 -> {
                        // Step 1: Owner Password
                        Text(
                            text = "Kadam 1: Owner Password Banayein",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Owner password se poore shop ka control, settings, aur web panel khulega. Kam se kam 6 akshar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = ownerPassword,
                            onValueChange = { ownerPassword = it; errorMessage = null },
                            label = { Text("Owner Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = ownerPasswordConfirm,
                            onValueChange = { ownerPasswordConfirm = it; errorMessage = null },
                            label = { Text("Confirm Owner Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage ?: "", color = BrandError, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (ownerPassword.length < 6) {
                                    errorMessage = "Password kam se kam 6 akshar ka hona chahiye"
                                } else if (ownerPassword != ownerPasswordConfirm) {
                                    errorMessage = "Passwords match nahi hue"
                                } else {
                                    authManager.setOwnerPassword(ownerPassword)
                                    errorMessage = null
                                    step = 2
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Text("Aage Badho (Next)")
                        }
                    }

                    2 -> {
                        // Step 2: Recovery Gmail
                        Text(
                            text = "Kadam 2: Recovery Gmail Verify Karein",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Password bhoolne par account reset karne ke liye Google account se verify karein. Typed address aur signed-in account barabar hona chahiye.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = recoveryEmailInput,
                            onValueChange = { recoveryEmailInput = it; errorMessage = null },
                            label = { Text("Recovery Gmail Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (verifiedRecoveryEmail != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandSuccess.copy(alpha = 0.15f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = BrandSuccess
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verified: $verifiedRecoveryEmail",
                                    color = BrandSuccess,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Button(
                            onClick = {
                                if (recoveryEmailInput.isBlank() || !recoveryEmailInput.contains("@")) {
                                    errorMessage = "Valid email address daliye"
                                    return@Button
                                }
                                onGoogleSignInClick { signedInEmail ->
                                    if (signedInEmail.trim().equals(recoveryEmailInput.trim(), ignoreCase = true)) {
                                        verifiedRecoveryEmail = signedInEmail.trim()
                                        authManager.setRecoveryGmail(signedInEmail.trim())
                                        errorMessage = null
                                    } else {
                                        errorMessage = "Signed-in account ($signedInEmail) typed email se match nahi hua"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = BrandBlue)
                        ) {
                            Text("Google Sign-In se Verify Karo")
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage ?: "", color = BrandError, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (verifiedRecoveryEmail == null) {
                                    errorMessage = "Pehle Google Sign-In se email verify karein"
                                } else {
                                    step = 3
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = verifiedRecoveryEmail != null,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Text("Aage Badho (Next)")
                        }
                    }

                    3 -> {
                        // Step 3: Staff Password
                        Text(
                            text = "Kadam 3: Staff Password Banayein",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dukaan ke ladke (staff) ke login ke liye password set karein. Staff sirf barcode scan aur print kar sakega.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = staffPassword,
                            onValueChange = { staffPassword = it; errorMessage = null },
                            label = { Text("Staff Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = staffPasswordConfirm,
                            onValueChange = { staffPasswordConfirm = it; errorMessage = null },
                            label = { Text("Confirm Staff Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage ?: "", color = BrandError, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (staffPassword.length < 6) {
                                    errorMessage = "Staff password kam se kam 6 characters ka ho"
                                } else if (staffPassword != staffPasswordConfirm) {
                                    errorMessage = "Passwords match nahi hue"
                                } else {
                                    authManager.setStaffPassword(staffPassword)
                                    errorMessage = null
                                    step = 4
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Text("Aage Badho (Next)")
                        }
                    }

                    4 -> {
                        // Step 4: Server Pairing
                        Text(
                            text = "Kadam 4: Website Server se Pair Karein",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Owner website (https://ambay-billing.pages.dev -> Settings -> Linked Devices) par 'Link a device' QR scan karo ya paste karo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                onScanQrClick { scannedText ->
                                    pairQrText = scannedText
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent, contentColor = Color.White)
                        ) {
                            Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("QR Code Scan Karo")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pairQrText,
                            onValueChange = { pairQrText = it; errorMessage = null },
                            label = { Text("Ya AMBPAIR code yahan paste karo") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage ?: "", color = BrandError, fontSize = 13.sp)
                        }

                        if (isPairSuccess) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandSuccess.copy(alpha = 0.15f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = BrandSuccess)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Server se pair ho gaya!", color = BrandSuccess, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (pairQrText.isBlank()) {
                                    errorMessage = "QR scan karein ya code paste karein"
                                    return@Button
                                }
                                val parsed = repository.parsePairingQr(pairQrText)
                                if (parsed == null) {
                                    errorMessage = "Invalid AMBPAIR1 QR code format"
                                    return@Button
                                }
                                isPairingLoading = true
                                scope.launch {
                                    val result = repository.redeemPairing(parsed)
                                    isPairingLoading = false
                                    if (result.isSuccess) {
                                        isPairSuccess = true
                                        errorMessage = null
                                    } else {
                                        errorMessage = "Pairing failed: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !isPairingLoading && pairQrText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            if (isPairingLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Pair Karein")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                authManager.setSetupCompleted(true)
                                onSetupCompleted()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandSuccess)
                        ) {
                            Text("Setup Pura Hua - Shuru Karein", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
