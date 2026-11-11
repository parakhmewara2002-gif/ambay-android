package com.ambay.billing.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambay.billing.core.auth.AuthManager
import com.ambay.billing.core.auth.UserRole
import com.ambay.billing.ui.theme.BrandBlue
import com.ambay.billing.ui.theme.BrandError

@Composable
fun ForgotPasswordDialog(
    role: UserRole,
    authManager: AuthManager,
    onDismiss: () -> Unit,
    onGoogleSignInRequest: (onEmailReceived: (String) -> Unit) -> Unit
) {
    var stage by remember { mutableStateOf(1) } // 1: Google verification, 2: New password input
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val maskedEmail = authManager.getMaskedRecoveryGmail()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (role == UserRole.OWNER) "Owner Password Reset" else "Staff Password Reset",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (stage == 1) {
                    if (role == UserRole.STAFF) {
                        Text(
                            text = "Staff apna password khud reset nahi kar sakta. Shop OWNER ko apne recovery Google account se verify karna hoga.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Apna registered recovery Gmail verify karein taaki aap naya password bana sakein.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Registered Gmail: $maskedEmail",
                        fontWeight = FontWeight.SemiBold,
                        color = BrandBlue,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = BrandError,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            onGoogleSignInRequest { signedInEmail ->
                                val registered = authManager.getRecoveryGmail()
                                if (registered != null && registered.equals(signedInEmail.trim(), ignoreCase = true)) {
                                    stage = 2
                                } else {
                                    errorMessage = "Ye Gmail registered nahi hai ($maskedEmail)"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Google Sign-In se Verify Karein")
                    }
                } else {
                    Text(
                        text = "Google verification kamiyab! Naya password banayein:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Naya Password (min 6 chars)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorMessage ?: "", color = BrandError, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (stage == 2) {
                Button(
                    onClick = {
                        if (newPassword.length < 6) {
                            errorMessage = "Password kam se kam 6 akshar ka hona chahiye"
                        } else if (newPassword != confirmPassword) {
                            errorMessage = "Dono passwords match nahi hue"
                        } else {
                            if (role == UserRole.OWNER) {
                                authManager.setOwnerPassword(newPassword)
                            } else {
                                authManager.setStaffPassword(newPassword)
                            }
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text("Save Karo")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
