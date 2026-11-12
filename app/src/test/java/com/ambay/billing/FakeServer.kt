package com.ambay.billing

import com.ambay.billing.core.CanonicalJson
import com.ambay.billing.core.ServerKeyEntry
import com.ambay.billing.core.TrustCrypto
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class FakeServer {
    val server = MockWebServer()
    private val kp: KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    val spkiB64u: String = TrustCrypto.b64uEncode(kp.public.encoded)
    val skid: String = TrustCrypto.kidOf(spkiB64u)
    val serverKeys: Map<String, ServerKeyEntry> = mapOf(skid to ServerKeyEntry("ES256", spkiB64u, "active"))

    var forceUnsigned = false
    var forceWrongNonce = false
    var forceWrongType = false

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1/health") -> {
                        val nonce = request.requestUrl?.queryParameter("nonce").orEmpty()
                        if (forceUnsigned) {
                            MockResponse().setResponseCode(200).setBody("""{"ok":true}""")
                        } else {
                            val type = if (forceWrongType) "wrong_type" else "health"
                            val outNonce = if (forceWrongNonce) "tampered_nonce" else nonce
                            val env = signEnvelope(
                                mapOf(
                                    "type" to type,
                                    "nonce" to outNonce,
                                    "ok" to true,
                                    "serverTs" to System.currentTimeMillis(),
                                    "trustSeq" to 1
                                )
                            )
                            MockResponse().setResponseCode(200).setBody(env)
                        }
                    }
                    path == "/v1/pair/redeem" -> {
                        val reqBody = request.body.readUtf8()
                        val nonceMatch = Regex("\"nonce\":\"([^\"]+)\"").find(reqBody)?.groupValues?.get(1).orEmpty()
                        val env = signEnvelope(
                            mapOf(
                                "type" to "pair",
                                "nonce" to nonceMatch,
                                "kid" to "test_kid",
                                "issuer" to "ambay_shop",
                                "deviceId" to "dev_1234",
                                "role" to "cashier",
                                "serverTs" to System.currentTimeMillis(),
                                "serverKeys" to mapOf(skid to mapOf("alg" to "ES256", "spki" to spkiB64u))
                            )
                        )
                        MockResponse().setResponseCode(201).setBody(env)
                    }
                    else -> MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}""")
                }
            }
        }
        server.start()
    }

    fun signEnvelope(payload: Map<String, Any?>): String {
        val canonP = CanonicalJson.stringify(payload)
        val msg = (TrustCrypto.SRV_DOMAIN + canonP).toByteArray(Charsets.UTF_8)
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(kp.private)
            update(msg)
        }.sign()
        val rawSig = TrustCrypto.b64uEncode(TrustCrypto.derToRaw64(sig))
        return """{"p":$canonP,"skid":"$skid","alg":"ES256","sig":"$rawSig"}"""
    }

    fun close() {
        server.shutdown()
    }
}
