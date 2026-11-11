package com.ambay.billing

import com.ambay.billing.core.crypto.Base64Url
import com.ambay.billing.core.crypto.CanonicalJson
import com.ambay.billing.core.crypto.SignatureConverter
import com.ambay.billing.core.model.ServerKey
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * In-memory FakeServer simulating the Cloudflare Worker registry for testing.
 * Signs response envelopes with an ES256 server key.
 */
class FakeServer {

    val serverKeyPair: KeyPair
    val skid: String = "sk_test_fake_server"
    val serverKey: ServerKey

    init {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        serverKeyPair = kpg.generateKeyPair()

        val spkiB64u = Base64Url.encode(serverKeyPair.public.encoded)
        serverKey = ServerKey(skid = skid, alg = "ES256", spki = spkiB64u)
    }

    fun signEnvelope(payload: Map<String, Any?>): String {
        val canonP = CanonicalJson.canon(payload)
        val msg = ("AMBSRV1\n" + canonP).toByteArray(Charsets.UTF_8)

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(serverKeyPair.private)
        signer.update(msg)
        val derSig = signer.sign()
        val rawSig = SignatureConverter.derToRaw(derSig)
        val sigB64u = Base64Url.encode(rawSig)

        val envelope = JSONObject().apply {
            put("p", JSONObject(payload))
            put("skid", skid)
            put("alg", "ES256")
            put("sig", sigB64u)
        }

        return envelope.toString()
    }

    fun handleHealth(nonce: String): String {
        val payload = mapOf(
            "type" to "health",
            "nonce" to nonce,
            "ok" to true,
            "serverTs" to System.currentTimeMillis()
        )
        return signEnvelope(payload)
    }
}
