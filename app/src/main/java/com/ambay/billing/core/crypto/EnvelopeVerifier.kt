package com.ambay.billing.core.crypto

import com.ambay.billing.core.model.ServerKey
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies signed server envelopes { p: {...}, skid: string, alg: string, sig: string }.
 * Verifies over "AMBSRV1\n" + canonicalJson(p).
 * Supports ES256 and Ed25519 algorithms.
 */
object EnvelopeVerifier {

    private const val SRV_DOMAIN = "AMBSRV1\n"

    sealed class VerifyResult {
        data class Success(val payloadJson: String, val payloadObj: JSONObject, val serverTs: Long) : VerifyResult()
        data class Error(val reason: String) : VerifyResult()
    }

    fun verify(
        envelopeJsonStr: String,
        pinnedServerKeys: Map<String, ServerKey>,
        expectedType: String? = null,
        expectedNonce: String? = null
    ): VerifyResult {
        return try {
            val root = JSONObject(envelopeJsonStr)
            if (!root.has("p") || !root.has("skid") || !root.has("sig")) {
                return VerifyResult.Error("Malformed server envelope: missing p, skid, or sig")
            }

            val pObj = root.getJSONObject("p")
            val skid = root.getString("skid")
            val sigB64u = root.getString("sig")
            val alg = root.optString("alg", "")

            val serverKey = pinnedServerKeys[skid]
                ?: return VerifyResult.Error("Unknown server key: $skid is not in pinned trust root")

            if (alg.isNotEmpty() && alg != serverKey.alg) {
                return VerifyResult.Error("Server algorithm mismatch: $alg vs ${serverKey.alg}")
            }

            // Verify envelope fields in p
            if (expectedType != null && pObj.optString("type") != expectedType) {
                return VerifyResult.Error("Unexpected response type: ${pObj.optString("type")}, expected $expectedType")
            }

            if (expectedNonce != null && pObj.optString("nonce") != expectedNonce) {
                return VerifyResult.Error("Replayed or mismatched response nonce: ${pObj.optString("nonce")}")
            }

            if (!pObj.has("serverTs") || pObj.isNull("serverTs")) {
                return VerifyResult.Error("Missing serverTs in server payload")
            }
            val serverTs = pObj.getLong("serverTs")

            // Canonicalize p object
            val pMap = jsonObjectToMap(pObj)
            val canonP = CanonicalJson.canon(pMap)
            val signedMessage = (SRV_DOMAIN + canonP).toByteArray(Charsets.UTF_8)
            val rawSig = Base64Url.decode(sigB64u)

            val verified = when (serverKey.alg) {
                "ES256" -> verifyEs256(serverKey.spki, signedMessage, rawSig)
                "Ed25519" -> verifyEd25519(serverKey.spki, signedMessage, rawSig)
                else -> return VerifyResult.Error("Unsupported server key algorithm: ${serverKey.alg}")
            }

            if (!verified) {
                return VerifyResult.Error("Server signature invalid: cryptographic check failed")
            }

            VerifyResult.Success(pObj.toString(), pObj, serverTs)
        } catch (e: Exception) {
            VerifyResult.Error("Server response verification failed safely: ${e.message}")
        }
    }

    private fun verifyEs256(spkiB64u: String, messageBytes: ByteArray, rawSig: ByteArray): Boolean {
        return try {
            val spkiBytes = Base64Url.decode(spkiB64u)
            val kf = KeyFactory.getInstance("EC")
            val pubKey = kf.generatePublic(X509EncodedKeySpec(spkiBytes))

            val derSig = SignatureConverter.rawToDer(rawSig)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(messageBytes)
            verifier.verify(derSig)
        } catch (e: Exception) {
            false
        }
    }

    private fun verifyEd25519(spkiB64u: String, messageBytes: ByteArray, rawSig: ByteArray): Boolean {
        return try {
            val spkiBytes = Base64Url.decode(spkiB64u)
            // SPKI for Ed25519: usually 44 bytes, the last 32 bytes are the raw public key
            val rawPublicKey = if (spkiBytes.size == 44 && spkiBytes[0] == 0x30.toByte()) {
                spkiBytes.copyOfRange(12, 44)
            } else if (spkiBytes.size == 32) {
                spkiBytes
            } else {
                return false
            }

            val pubParams = Ed25519PublicKeyParameters(rawPublicKey, 0)
            val signer = Ed25519Signer()
            signer.init(false, pubParams)
            signer.update(messageBytes, 0, messageBytes.size)
            signer.verifySignature(rawSig)
        } catch (e: Exception) {
            false
        }
    }

    private fun jsonObjectToMap(jsonObj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObj.get(key)
            map[key] = jsonValueToKotlin(value)
        }
        return map
    }

    private fun jsonValueToKotlin(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL, null -> null
            is JSONObject -> jsonObjectToMap(value)
            is org.json.JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    list.add(jsonValueToKotlin(value.get(i)))
                }
                list
            }
            is Int -> value.toLong()
            is Long -> value
            is Double -> {
                if (Math.floor(value) == value) value.toLong() else value
            }
            else -> value
        }
    }
}
