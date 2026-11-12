package com.ambay.billing.data.local

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ambay.billing.BuildConfig
import com.ambay.billing.core.ServerKeyEntry
import com.ambay.billing.core.TrustBundle
import com.ambay.billing.core.TrustCrypto
import com.ambay.billing.core.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ambay_secure_prefs_v2",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val PBKDF2_ITERATIONS = 210_000
        private const val KEY_LENGTH_BITS = 256
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEVICE_KEY_ALIAS_PREFIX = "ambay_device_ec_"

        private const val KEY_WIZARD_DONE = "wizard_done"
        private const val KEY_OWNER_HASH = "owner_pwd_hash"
        private const val KEY_OWNER_SALT = "owner_pwd_salt"
        private const val KEY_STAFF_HASH = "staff_pwd_hash"
        private const val KEY_STAFF_SALT = "staff_pwd_salt"
        private const val KEY_RECOVERY_GMAIL = "recovery_gmail"
        private const val KEY_STAFF_BIOMETRIC = "staff_biometric_enabled"
        private const val KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes"
        private const val KEY_LAST_BG_TS = "last_background_ts"

        private const val KEY_OWNER_FAILS = "owner_fails"
        private const val KEY_OWNER_LOCK_UNTIL = "owner_lock_until"
        private const val KEY_STAFF_FAILS = "staff_fails"
        private const val KEY_STAFF_LOCK_UNTIL = "staff_lock_until"

        private const val KEY_BASE_URL = "server_base_url"
        private const val KEY_ACTIVE_KID = "active_device_kid"
        private const val KEY_DEVICE_ID = "paired_device_id"
        private const val KEY_ISSUER_ID = "paired_issuer_id"
        private const val KEY_PAIRED_ROLE = "paired_role"
        private const val KEY_SERVER_KEYS = "pinned_server_keys"
        private const val KEY_TRUST_BUNDLE = "trust_bundle"
        private const val KEY_CLOCK_OFFSET = "clock_offset_ms"
        private const val KEY_SALES_SYNC_SEQ = "sales_sync_seq"
        private const val KEY_ITEMS_SYNC_SEQ = "items_sync_seq"
        private const val KEY_DEVICE_REVOKED = "device_revoked"
        private const val KEY_SAVED_PRINTER_MAC = "saved_printer_mac"
        private const val KEY_SAVED_PRINTER_NAME = "saved_printer_name"
        private const val KEY_SAVED_PRINTER_WIDTH = "saved_printer_width"
    }

    fun isWizardDone(): Boolean = prefs.getBoolean(KEY_WIZARD_DONE, false)
    fun setWizardDone(done: Boolean) = prefs.edit().putBoolean(KEY_WIZARD_DONE, done).apply()

    fun getRecoveryGmail(): String = prefs.getString(KEY_RECOVERY_GMAIL, "").orEmpty()
    fun setRecoveryGmail(email: String) = prefs.edit().putString(KEY_RECOVERY_GMAIL, email.trim().lowercase()).apply()

    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2 || parts[0].isEmpty()) return "***"
        val local = parts[0]
        val shown = local.take(2)
        return "$shown***@${parts[1]}"
    }

    /**
     * Biometric is strictly for STAFF only. Owner ALWAYS requires password.
     */
    fun isStaffBiometricEnabled(): Boolean = prefs.getBoolean(KEY_STAFF_BIOMETRIC, false)
    fun setStaffBiometricEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_STAFF_BIOMETRIC, enabled).apply()

    fun getAutoLockMinutes(): Int = prefs.getInt(KEY_AUTO_LOCK_MINUTES, 2)
    fun setAutoLockMinutes(min: Int) = prefs.edit().putInt(KEY_AUTO_LOCK_MINUTES, min.coerceIn(0, 60)).apply()

    fun getLastBackgroundTs(): Long = prefs.getLong(KEY_LAST_BG_TS, 0L)
    fun setLastBackgroundTs(ts: Long) = prefs.edit().putLong(KEY_LAST_BG_TS, ts).apply()

    fun setPassword(role: UserRole, password: String) {
        require(password.length >= 6) { "Password kam se kam 6 characters ka hona chahiye" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt)
        val saltB64 = TrustCrypto.b64uEncode(salt)
        val hashB64 = TrustCrypto.b64uEncode(hash)
        val editor = prefs.edit()
        when (role) {
            UserRole.OWNER -> {
                editor.putString(KEY_OWNER_SALT, saltB64)
                editor.putString(KEY_OWNER_HASH, hashB64)
                editor.putInt(KEY_OWNER_FAILS, 0)
                editor.putLong(KEY_OWNER_LOCK_UNTIL, 0L)
            }
            UserRole.STAFF -> {
                editor.putString(KEY_STAFF_SALT, saltB64)
                editor.putString(KEY_STAFF_HASH, hashB64)
                editor.putInt(KEY_STAFF_FAILS, 0)
                editor.putLong(KEY_STAFF_LOCK_UNTIL, 0L)
            }
        }
        editor.apply()
    }

    fun getLockoutRemainingMs(role: UserRole, nowMs: Long = System.currentTimeMillis()): Long {
        val key = if (role == UserRole.OWNER) KEY_OWNER_LOCK_UNTIL else KEY_STAFF_LOCK_UNTIL
        val until = prefs.getLong(key, 0L)
        return (until - nowMs).coerceAtLeast(0L)
    }

    fun verifyPassword(role: UserRole, password: String, nowMs: Long = System.currentTimeMillis()): PasswordCheckResult {
        val lockRem = getLockoutRemainingMs(role, nowMs)
        if (lockRem > 0L) {
            return PasswordCheckResult.LockedOut(lockRem)
        }
        val saltKey = if (role == UserRole.OWNER) KEY_OWNER_SALT else KEY_STAFF_SALT
        val hashKey = if (role == UserRole.OWNER) KEY_OWNER_HASH else KEY_STAFF_HASH
        val failsKey = if (role == UserRole.OWNER) KEY_OWNER_FAILS else KEY_STAFF_FAILS
        val lockKey = if (role == UserRole.OWNER) KEY_OWNER_LOCK_UNTIL else KEY_STAFF_LOCK_UNTIL

        val saltB64 = prefs.getString(saltKey, null) ?: return PasswordCheckResult.NotSet
        val hashB64 = prefs.getString(hashKey, null) ?: return PasswordCheckResult.NotSet

        val salt = TrustCrypto.b64uDecode(saltB64)
        val expected = TrustCrypto.b64uDecode(hashB64)
        val actual = pbkdf2(password, salt)

        var diff = expected.size xor actual.size
        for (i in expected.indices) {
            diff = diff or (expected[i].toInt() xor actual[i].toInt())
        }
        return if (diff == 0) {
            prefs.edit().putInt(failsKey, 0).putLong(lockKey, 0L).apply()
            PasswordCheckResult.Success
        } else {
            val fails = prefs.getInt(failsKey, 0) + 1
            val editor = prefs.edit().putInt(failsKey, fails)
            var lockMs = 0L
            if (fails >= 5) {
                val exp = (fails - 5).coerceAtMost(4)
                val secs = (30L shl exp).coerceAtMost(300L) // 30s, 60s, 120s, 240s, max 300s (5m)
                lockMs = secs * 1000L
                editor.putLong(lockKey, nowMs + lockMs)
            }
            editor.apply()
            if (lockMs > 0L) PasswordCheckResult.LockedOut(lockMs)
            else PasswordCheckResult.Wrong(attemptsRemaining = (5 - fails).coerceAtLeast(0))
        }
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    // ---- Server / Pairing / Keystore ----

    fun getBaseUrl(): String =
        prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.BASE_URL

    fun setBaseUrl(url: String) =
        prefs.edit().putString(KEY_BASE_URL, url.trim().trimEnd('/')).apply()

    fun getDeviceKid(): String? = prefs.getString(KEY_ACTIVE_KID, null)
    fun getDeviceId(): String = prefs.getString(KEY_DEVICE_ID, null) ?: run {
        val id = "and_${TrustCrypto.randomB64u(6)}"
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        id
    }

    fun setPairingSession(kid: String, deviceId: String, issuer: String, role: String) {
        prefs.edit()
            .putString(KEY_ACTIVE_KID, kid)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_ISSUER_ID, issuer)
            .putString(KEY_PAIRED_ROLE, role)
            .putBoolean(KEY_DEVICE_REVOKED, false)
            .apply()
    }

    fun getIssuerId(): String = prefs.getString(KEY_ISSUER_ID, "").orEmpty()
    fun getPairedRole(): String = prefs.getString(KEY_PAIRED_ROLE, "").orEmpty()
    fun isDeviceRevoked(): Boolean = prefs.getBoolean(KEY_DEVICE_REVOKED, false)
    fun setDeviceRevoked(revoked: Boolean) = prefs.edit().putBoolean(KEY_DEVICE_REVOKED, revoked).apply()

    fun wipePairingSession() {
        prefs.edit()
            .remove(KEY_ACTIVE_KID)
            .remove(KEY_ISSUER_ID)
            .remove(KEY_PAIRED_ROLE)
            .putBoolean(KEY_DEVICE_REVOKED, true)
            .apply()
    }

    fun getServerKeys(): Map<String, ServerKeyEntry> {
        val raw = prefs.getString(KEY_SERVER_KEYS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, ServerKeyEntry>>(raw) }.getOrDefault(emptyMap())
    }

    fun setServerKeys(keys: Map<String, ServerKeyEntry>) {
        prefs.edit().putString(KEY_SERVER_KEYS, json.encodeToString(keys)).apply()
    }

    fun getTrustBundle(): TrustBundle? {
        val raw = prefs.getString(KEY_TRUST_BUNDLE, null) ?: return null
        return runCatching { json.decodeFromString<TrustBundle>(raw) }.getOrNull()
    }

    fun setTrustBundle(bundle: TrustBundle) {
        prefs.edit().putString(KEY_TRUST_BUNDLE, json.encodeToString(bundle)).apply()
        if (bundle.serverKeys.isNotEmpty()) {
            setServerKeys(bundle.serverKeys)
        }
    }

    fun getClockOffsetMs(): Long = prefs.getLong(KEY_CLOCK_OFFSET, 0L)
    fun setClockOffsetMs(offsetMs: Long) = prefs.edit().putLong(KEY_CLOCK_OFFSET, offsetMs).apply()

    fun getSalesSyncSeq(): Long = prefs.getLong(KEY_SALES_SYNC_SEQ, 0L)
    fun setSalesSyncSeq(seq: Long) = prefs.edit().putLong(KEY_SALES_SYNC_SEQ, seq).apply()

    fun getItemsSyncSeq(): Long = prefs.getLong(KEY_ITEMS_SYNC_SEQ, 0L)
    fun setItemsSyncSeq(seq: Long) = prefs.edit().putLong(KEY_ITEMS_SYNC_SEQ, seq).apply()

    // ---- Saved ESC/POS Printer ----
    fun getSavedPrinter(): Triple<String, String, Int>? {
        val mac = prefs.getString(KEY_SAVED_PRINTER_MAC, null) ?: return null
        val name = prefs.getString(KEY_SAVED_PRINTER_NAME, "Bluetooth Printer").orEmpty()
        val width = prefs.getInt(KEY_SAVED_PRINTER_WIDTH, 58)
        return Triple(mac, name, width)
    }

    fun setSavedPrinter(mac: String, name: String, widthMm: Int) {
        prefs.edit()
            .putString(KEY_SAVED_PRINTER_MAC, mac)
            .putString(KEY_SAVED_PRINTER_NAME, name)
            .putInt(KEY_SAVED_PRINTER_WIDTH, widthMm)
            .apply()
    }

    // ---- Android Keystore EC P-256 Device Key (StrongBox if available) ----

    data class DeviceKeyInfo(val kid: String, val alg: String = "ES256", val spkiB64u: String)

    fun ensureDeviceKey(forceNew: Boolean = false): DeviceKeyInfo {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKid = getDeviceKid()
        if (!forceNew && existingKid != null && ks.containsAlias(DEVICE_KEY_ALIAS_PREFIX + existingKid)) {
            val cert = ks.getCertificate(DEVICE_KEY_ALIAS_PREFIX + existingKid)
            val spki = TrustCrypto.b64uEncode(cert.publicKey.encoded)
            return DeviceKeyInfo(existingKid, "ES256", spki)
        }

        val tempAlias = DEVICE_KEY_ALIAS_PREFIX + "tmp_" + System.currentTimeMillis()
        generateEcKeyPair(tempAlias)
        val cert = ks.getCertificate(tempAlias)
        val spkiBytes = cert.publicKey.encoded
        val spkiB64u = TrustCrypto.b64uEncode(spkiBytes)
        val kid = TrustCrypto.kidOf(spkiB64u)

        // Generate under permanent kid alias
        val permAlias = DEVICE_KEY_ALIAS_PREFIX + kid
        if (!ks.containsAlias(permAlias)) {
            ks.deleteEntry(tempAlias)
            generateEcKeyPair(permAlias)
        } else {
            ks.deleteEntry(tempAlias)
        }
        val finalSpki = TrustCrypto.b64uEncode(ks.getCertificate(permAlias).publicKey.encoded)
        val finalKid = TrustCrypto.kidOf(finalSpki)
        prefs.edit().putString(KEY_ACTIVE_KID, finalKid).apply()
        return DeviceKeyInfo(finalKid, "ES256", finalSpki)
    }

    private fun generateEcKeyPair(alias: String) {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val hasStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox) {
            try {
                builder.setIsStrongBoxBacked(true)
                kpg.initialize(builder.build())
                kpg.generateKeyPair()
                return
            } catch (_: Throwable) {
                // Fall back to TEE if StrongBox keygen fails
                builder.setIsStrongBoxBacked(false)
            }
        }
        kpg.initialize(builder.build())
        kpg.generateKeyPair()
    }

    fun signWithDeviceKey(kid: String, message: ByteArray): String {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val alias = DEVICE_KEY_ALIAS_PREFIX + kid
        val privKey = ks.getKey(alias, null) as? PrivateKey
            ?: error("Device signing key $kid not found in Keystore")
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privKey)
        sig.update(message)
        val der = sig.sign()
        val raw64 = TrustCrypto.derToRaw64(der)
        return TrustCrypto.b64uEncode(raw64)
    }
}

sealed class PasswordCheckResult {
    data object Success : PasswordCheckResult()
    data object NotSet : PasswordCheckResult()
    data class Wrong(val attemptsRemaining: Int) : PasswordCheckResult()
    data class LockedOut(val remainingMs: Long) : PasswordCheckResult()
}
