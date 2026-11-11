package com.ambay.billing.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ambay.billing.core.crypto.PasswordHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserRole {
    NONE,
    STAFF,
    OWNER
}

class AuthManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ambay_auth_encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _currentRole = MutableStateFlow(UserRole.NONE)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun isSetupCompleted(): Boolean {
        return prefs.getBoolean(KEY_SETUP_COMPLETED, false)
    }

    fun setSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
    }

    fun setOwnerPassword(password: String) {
        require(password.length >= 6) { "Password must be at least 6 characters" }
        val hash = PasswordHasher.hashPassword(password)
        prefs.edit().putString(KEY_OWNER_HASH, hash).apply()
        resetFailedAttempts(UserRole.OWNER)
    }

    fun setStaffPassword(password: String) {
        require(password.length >= 6) { "Staff password must be at least 6 characters" }
        val hash = PasswordHasher.hashPassword(password)
        prefs.edit().putString(KEY_STAFF_HASH, hash).apply()
        resetFailedAttempts(UserRole.STAFF)
    }

    fun setRecoveryGmail(email: String) {
        val trimmed = email.trim().lowercase()
        prefs.edit().putString(KEY_RECOVERY_GMAIL, trimmed).apply()
    }

    fun getRecoveryGmail(): String? {
        return prefs.getString(KEY_RECOVERY_GMAIL, null)
    }

    fun getMaskedRecoveryGmail(): String {
        val email = getRecoveryGmail() ?: return "No Gmail registered"
        return maskEmail(email)
    }

    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "***"
        val name = parts[0]
        val domain = parts[1]
        val visible = if (name.length > 2) name.take(2) else name.take(1)
        return "$visible***@$domain"
    }

    fun verifyOwnerPassword(password: String): Boolean {
        val lockoutSeconds = getRemainingLockoutSeconds(UserRole.OWNER)
        if (lockoutSeconds > 0) return false

        val hash = prefs.getString(KEY_OWNER_HASH, null) ?: return false
        val ok = PasswordHasher.verifyPassword(password, hash)
        if (ok) {
            resetFailedAttempts(UserRole.OWNER)
            _currentRole.value = UserRole.OWNER
            _isLocked.value = false
            enableBiometricAfterFirstLogin()
            return true
        } else {
            recordFailedAttempt(UserRole.OWNER)
            return false
        }
    }

    fun verifyStaffPassword(password: String): Boolean {
        val lockoutSeconds = getRemainingLockoutSeconds(UserRole.STAFF)
        if (lockoutSeconds > 0) return false

        val hash = prefs.getString(KEY_STAFF_HASH, null) ?: return false
        val ok = PasswordHasher.verifyPassword(password, hash)
        if (ok) {
            resetFailedAttempts(UserRole.STAFF)
            _currentRole.value = UserRole.STAFF
            _isLocked.value = false
            enableBiometricAfterFirstLogin()
            return true
        } else {
            recordFailedAttempt(UserRole.STAFF)
            return false
        }
    }

    /** Biometric unlock is allowed for STAFF only; OWNER always needs the password (any enrolled fingerprint on the phone would otherwise open owner mode). */
    fun unlockWithBiometrics(role: UserRole): Boolean {
        if (role != UserRole.STAFF || !isBiometricEnabled()) return false
        _currentRole.value = role
        _isLocked.value = false
        return true
    }

    fun lock() {
        _isLocked.value = true
        _currentRole.value = UserRole.NONE
    }

    fun switchRole(role: UserRole) {
        _currentRole.value = role
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    private fun enableBiometricAfterFirstLogin() {
        if (!prefs.contains(KEY_BIOMETRIC_ENABLED)) {
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply()
        }
    }

    fun getAutoLockTimeoutMinutes(): Int {
        return prefs.getInt(KEY_AUTOLOCK_MINUTES, 2)
    }

    fun setAutoLockTimeoutMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_AUTOLOCK_MINUTES, minutes).apply()
    }

    // Lockout management
    fun getRemainingLockoutSeconds(role: UserRole): Long {
        val key = if (role == UserRole.OWNER) KEY_LOCKOUT_UNTIL_OWNER else KEY_LOCKOUT_UNTIL_STAFF
        val until = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        return if (until > now) (until - now) / 1000L else 0L
    }

    private fun recordFailedAttempt(role: UserRole) {
        val attemptsKey = if (role == UserRole.OWNER) KEY_FAILED_ATTEMPTS_OWNER else KEY_FAILED_ATTEMPTS_STAFF
        val lockoutKey = if (role == UserRole.OWNER) KEY_LOCKOUT_UNTIL_OWNER else KEY_LOCKOUT_UNTIL_STAFF

        val attempts = prefs.getInt(attemptsKey, 0) + 1
        prefs.edit().putInt(attemptsKey, attempts).apply()

        if (attempts >= 5) {
            // Exponential lockout: 30s, 60s, 120s, 240s, max 300s (5 min)
            val factor = attempts - 5
            val seconds = when (factor) {
                0 -> 30L
                1 -> 60L
                2 -> 120L
                3 -> 240L
                else -> 300L
            }
            val until = System.currentTimeMillis() + (seconds * 1000L)
            prefs.edit().putLong(lockoutKey, until).apply()
        }
    }

    private fun resetFailedAttempts(role: UserRole) {
        val attemptsKey = if (role == UserRole.OWNER) KEY_FAILED_ATTEMPTS_OWNER else KEY_FAILED_ATTEMPTS_STAFF
        val lockoutKey = if (role == UserRole.OWNER) KEY_LOCKOUT_UNTIL_OWNER else KEY_LOCKOUT_UNTIL_STAFF
        prefs.edit()
            .putInt(attemptsKey, 0)
            .putLong(lockoutKey, 0L)
            .apply()
    }

    companion object {
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_OWNER_HASH = "owner_password_hash"
        private const val KEY_STAFF_HASH = "staff_password_hash"
        private const val KEY_RECOVERY_GMAIL = "recovery_gmail"
        private const val KEY_FAILED_ATTEMPTS_OWNER = "failed_attempts_owner"
        private const val KEY_FAILED_ATTEMPTS_STAFF = "failed_attempts_staff"
        private const val KEY_LOCKOUT_UNTIL_OWNER = "lockout_until_owner"
        private const val KEY_LOCKOUT_UNTIL_STAFF = "lockout_until_staff"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTOLOCK_MINUTES = "autolock_minutes"
    }
}
