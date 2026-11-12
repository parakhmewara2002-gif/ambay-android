package com.ambay.billing.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambay.billing.BuildConfig
import com.ambay.billing.core.UserRole
import com.ambay.billing.data.local.PasswordCheckResult
import com.ambay.billing.data.local.SecurePrefs
import com.ambay.billing.data.repository.SyncRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val wizardDone: Boolean = false,
    val wizardStep: Int = 0, // 0=owner pwd, 1=recovery gmail, 2=staff pwd, 3=pair qr
    val selectedRole: UserRole? = null,
    val loggedInRole: UserRole? = null,
    val errorMsg: String? = null,
    val infoMsg: String? = null,
    val isBusy: Boolean = false,
    val maskedRecoveryGmail: String = "",
    val staffBiometricEnabled: Boolean = false,
    val pairedKid: String? = null,
    val deviceRevoked: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(
        AuthUiState(
            wizardDone = securePrefs.isWizardDone(),
            maskedRecoveryGmail = securePrefs.maskEmail(securePrefs.getRecoveryGmail()),
            staffBiometricEnabled = securePrefs.isStaffBiometricEnabled(),
            pairedKid = securePrefs.getDeviceKid(),
            deviceRevoked = securePrefs.isDeviceRevoked()
        )
    )
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun selectRole(role: UserRole?) {
        _ui.update { it.copy(selectedRole = role, errorMsg = null, infoMsg = null) }
    }

    fun loginWithPassword(role: UserRole, password: String) {
        when (val res = securePrefs.verifyPassword(role, password)) {
            is PasswordCheckResult.Success -> {
                _ui.update { it.copy(loggedInRole = role, errorMsg = null, selectedRole = null) }
            }
            is PasswordCheckResult.NotSet -> {
                _ui.update { it.copy(errorMsg = "Password set nahi hai — setup wizard poora karo") }
            }
            is PasswordCheckResult.Wrong -> {
                _ui.update { it.copy(errorMsg = "Galat password (${res.attemptsRemaining} koshish bachi)") }
            }
            is PasswordCheckResult.LockedOut -> {
                val secs = (res.remainingMs / 1000L).coerceAtLeast(1L)
                _ui.update { it.copy(errorMsg = "Bahut galat koshish! $secs second baad try karo") }
            }
        }
    }

    fun onStaffBiometricSuccess() {
        // Guard: biometric is strictly for STAFF only
        if (securePrefs.isStaffBiometricEnabled()) {
            _ui.update { it.copy(loggedInRole = UserRole.STAFF, errorMsg = null, selectedRole = null) }
        }
    }

    fun switchToStaffView() {
        if (_ui.value.loggedInRole == UserRole.OWNER) {
            _ui.update { it.copy(loggedInRole = UserRole.STAFF) }
        }
    }

    fun logout() {
        _ui.update { it.copy(loggedInRole = null, selectedRole = null, errorMsg = null) }
    }

    fun saveWizardOwnerPassword(pwd: String, confirm: String) {
        if (pwd.length < 6) {
            _ui.update { it.copy(errorMsg = "Owner password kam se kam 6 characters ka ho") }
            return
        }
        if (pwd != confirm) {
            _ui.update { it.copy(errorMsg = "Dono password match nahi kar rahe") }
            return
        }
        securePrefs.setPassword(UserRole.OWNER, pwd)
        _ui.update { it.copy(wizardStep = 1, errorMsg = null) }
    }

    fun verifyGoogleAccount(activityContext: Context, typedEmail: String, onVerified: (String) -> Unit) {
        val cleanTyped = typedEmail.trim().lowercase()
        if (!cleanTyped.contains("@")) {
            _ui.update { it.copy(errorMsg = "Sahi Gmail address likho") }
            return
        }
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) {
            _ui.update { it.copy(errorMsg = "GOOGLE_WEB_CLIENT_ID configured nahi hai (gradle.properties / CI check karo)") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, errorMsg = null) }
            try {
                val cm = CredentialManager.create(activityContext)
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .build()
                val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val res = cm.getCredential(activityContext, req)
                val cred = GoogleIdTokenCredential.createFrom(res.credential.data)
                val signedInEmail = cred.id.trim().lowercase()
                if (signedInEmail != cleanTyped) {
                    _ui.update {
                        it.copy(
                            isBusy = false,
                            errorMsg = "Signed-in Gmail ($signedInEmail) typed Gmail se match nahi karta"
                        )
                    }
                } else {
                    _ui.update { it.copy(isBusy = false, errorMsg = null) }
                    onVerified(signedInEmail)
                }
            } catch (e: Throwable) {
                _ui.update { it.copy(isBusy = false, errorMsg = "Google Sign-In fail: ${e.message}") }
            }
        }
    }

    fun saveWizardRecoveryGmail(verifiedEmail: String) {
        securePrefs.setRecoveryGmail(verifiedEmail)
        _ui.update {
            it.copy(
                wizardStep = 2,
                maskedRecoveryGmail = securePrefs.maskEmail(verifiedEmail),
                errorMsg = null
            )
        }
    }

    fun saveWizardStaffPassword(pwd: String, confirm: String) {
        if (pwd.length < 6) {
            _ui.update { it.copy(errorMsg = "Staff password kam se kam 6 characters ka ho") }
            return
        }
        if (pwd != confirm) {
            _ui.update { it.copy(errorMsg = "Dono password match nahi kar rahe") }
            return
        }
        securePrefs.setPassword(UserRole.STAFF, pwd)
        _ui.update { it.copy(wizardStep = 3, errorMsg = null) }
    }

    fun pairWithQr(qrText: String, finishWizardAfter: Boolean = true) {
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, errorMsg = null) }
            val res = syncRepository.pairWithQrText(qrText)
            res.onSuccess { kid ->
                if (finishWizardAfter) securePrefs.setWizardDone(true)
                _ui.update {
                    it.copy(
                        isBusy = false,
                        pairedKid = kid,
                        wizardDone = if (finishWizardAfter) true else it.wizardDone,
                        deviceRevoked = false,
                        infoMsg = "Device pair ho gaya! Key ID: $kid"
                    )
                }
            }.onFailure { err ->
                _ui.update { it.copy(isBusy = false, errorMsg = err.message ?: "Pairing fail hui") }
            }
        }
    }

    fun skipPairingAndFinishWizard() {
        securePrefs.setWizardDone(true)
        _ui.update { it.copy(wizardDone = true, errorMsg = null) }
    }

    fun resetPasswordAfterGoogleVerify(targetRole: UserRole, newPwd: String) {
        if (newPwd.length < 6) {
            _ui.update { it.copy(errorMsg = "Password kam se kam 6 characters ka ho") }
            return
        }
        securePrefs.setPassword(targetRole, newPwd)
        _ui.update { it.copy(infoMsg = "${targetRole.name} password update ho gaya", errorMsg = null) }
    }

    fun getRecoveryGmailRaw(): String = securePrefs.getRecoveryGmail()

    fun clearMessages() {
        _ui.update { it.copy(errorMsg = null, infoMsg = null) }
    }
}
