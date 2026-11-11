package com.ambay.billing.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Manages the device's hardware-backed EC P-256 (ES256) key pair in AndroidKeyStore.
 * Non-exportable private key; public key exported as SubjectPublicKeyInfo (SPKI) DER.
 */
class DeviceKeyManager(private val keyAlias: String = "ambay_device_es256_key") {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun getOrCreateKey(): Pair<String, String> {
        if (!keyStore.containsAlias(keyAlias)) {
            generateKeyPair()
        }
        val spki = getPublicKeySpki()
        val kid = computeKid(spki)
        return Pair(kid, spki)
    }

    private fun generateKeyPair() {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)

        // Attempt StrongBox if available on Android 9+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
                kpg.initialize(builder.build())
                kpg.generateKeyPair()
                return
            } catch (e: Exception) {
                // Fallback to TEE if StrongBox is unavailable on device
                builder.setIsStrongBoxBacked(false)
            }
        }

        kpg.initialize(builder.build())
        kpg.generateKeyPair()
    }

    fun getPublicKey(): PublicKey {
        return keyStore.getCertificate(keyAlias).publicKey
    }

    fun getPrivateKey(): PrivateKey {
        return keyStore.getKey(keyAlias, null) as PrivateKey
    }

    /**
     * Returns base64url SubjectPublicKeyInfo (SPKI) DER without padding.
     */
    fun getPublicKeySpki(): String {
        val spkiBytes = getPublicKey().encoded
        return Base64Url.encode(spkiBytes)
    }

    /**
     * kid = base64url(first 12 bytes of SHA-256(spki bytes))
     */
    fun computeKid(spkiB64u: String): String {
        val spkiBytes = Base64Url.decode(spkiB64u)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(spkiBytes)
        val first12 = digest.copyOfRange(0, 12)
        return Base64Url.encode(first12)
    }

    /**
     * Signs bytes with SHA256withECDSA, converts DER to raw 64-byte r||s, base64url encoded.
     */
    fun sign(bytes: ByteArray): String {
        val privateKey = getPrivateKey()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(bytes)
        val derSig = signer.sign()
        val rawSig = SignatureConverter.derToRaw(derSig)
        return Base64Url.encode(rawSig)
    }

    /**
     * Builds and signs the proof-of-possession (POP) string for pairing redeem:
     * pop = sign( "AMBENROLL1\n" + sha256hex("pair:"+token) + "\n" + kid + "\n" + nonce )
     */
    fun generatePairingPop(token: String, kid: String, nonce: String): String {
        val tokenHash = sha256Hex("pair:$token")
        val popMessage = "AMBENROLL1\n$tokenHash\n$kid\n$nonce"
        return sign(popMessage.toByteArray(Charsets.UTF_8))
    }

    /**
     * Builds and signs request message for authenticated endpoints:
     * sign("AMBREQ1\n" + METHOD + "\n" + pathAndQuery + "\n" + ts + "\n" + nonce + "\n" + sha256hex(exactBodyString or ""))
     */
    fun signRequest(method: String, pathAndQuery: String, ts: String, nonce: String, bodyString: String): String {
        val bodyHash = sha256Hex(bodyString)
        val msg = "AMBREQ1\n${method.uppercase()}\n$pathAndQuery\n$ts\n$nonce\n$bodyHash"
        return sign(msg.toByteArray(Charsets.UTF_8))
    }

    fun deleteKey() {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    companion object {
        fun sha256Hex(input: String): String {
            if (input.isEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                return bytesToHex(md.digest(ByteArray(0)))
            }
            val md = MessageDigest.getInstance("SHA-256")
            return bytesToHex(md.digest(input.toByteArray(Charsets.UTF_8)))
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val sb = java.lang.StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }
    }
}
