package com.ambay.billing

import com.ambay.billing.core.CanonicalJson
import com.ambay.billing.core.EnvelopeVerifyResult
import com.ambay.billing.core.ServerEnvelope
import com.ambay.billing.core.TrustCrypto
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnvelopeAndPairingTest {

    private lateinit var fakeServer: FakeServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeServer = FakeServer().apply { start() }
    }

    @After
    fun teardown() {
        fakeServer.close()
    }

    @Test
    fun `verifyEnvelope accepts valid signed response and rejects nonce or type mismatch`() {
        val nonce = "abcDEF123456"
        val raw = fakeServer.signEnvelope(
            mapOf("type" to "health", "nonce" to nonce, "ok" to true, "serverTs" to 1700000000000L)
        )
        val env = json.decodeFromString<ServerEnvelope>(raw)

        val ok = TrustCrypto.verifyEnvelope(env, fakeServer.serverKeys, "health", nonce)
        assertTrue(ok is EnvelopeVerifyResult.Valid)

        val badNonce = TrustCrypto.verifyEnvelope(env, fakeServer.serverKeys, "health", "wrong_nonce")
        assertTrue(badNonce is EnvelopeVerifyResult.Invalid)

        val badType = TrustCrypto.verifyEnvelope(env, fakeServer.serverKeys, "pair", nonce)
        assertTrue(badType is EnvelopeVerifyResult.Invalid)
    }

    @Test
    fun `parsePairingQr parses AMBPAIR1 payload`() {
        val obj = mapOf(
            "v" to 1,
            "u" to "https://ambay.example.workers.dev",
            "t" to "a".repeat(40),
            "i" to "ambay_shop",
            "e" to 1800000000000L,
            "r" to mapOf("skid" to fakeServer.skid, "alg" to "ES256", "spki" to fakeServer.spkiB64u)
        )
        val qr = "AMBPAIR1." + TrustCrypto.b64uEncode(CanonicalJson.stringify(obj).toByteArray())
        val parsed = TrustCrypto.parsePairingQr(qr)
        assertNotNull(parsed)
        assertEquals("ambay_shop", parsed?.i)
        assertEquals(fakeServer.skid, parsed?.r?.skid)
    }
}
