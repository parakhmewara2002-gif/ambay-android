package com.ambay.billing.core.crypto

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 password hasher with >= 210,000 iterations.
 * Salted, constant-time verification.
 */
object PasswordHasher {

    private const val ITERATIONS = 210_000
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hashPassword(password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)

        val hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val saltHex = bytesToHex(salt)
        val hashHex = bytesToHex(hash)

        return "pbkdf2:$ITERATIONS:$saltHex:$hashHex"
    }

    fun verifyPassword(password: String, storedHashRecord: String): Boolean {
        try {
            val parts = storedHashRecord.split(":")
            if (parts.size != 4 || parts[0] != "pbkdf2") return false

            val iterations = parts[1].toInt()
            val salt = hexToBytes(parts[2])
            val expectedHash = hexToBytes(parts[3])

            val actualHash = pbkdf2(password.toCharArray(), salt, iterations, expectedHash.size * 8)
            return constantTimeEquals(expectedHash, actualHash)
        } catch (e: Exception) {
            return false
        }
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLength)
        val skf = SecretKeyFactory.getInstance(ALGORITHM)
        return skf.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
