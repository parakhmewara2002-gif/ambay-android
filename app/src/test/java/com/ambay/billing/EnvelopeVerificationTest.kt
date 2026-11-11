package com.ambay.billing

import com.ambay.billing.core.crypto.EnvelopeVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeVerificationTest {

    @Test
    fun testValidServerEnvelopeVerification() {
        val fakeServer = FakeServer()
        val nonce = "test_nonce_abc123"
        val envelopeJson = fakeServer.handleHealth(nonce)

        val pinnedKeys = mapOf(fakeServer.skid to fakeServer.serverKey)

        val result = EnvelopeVerifier.verify(
            envelopeJsonStr = envelopeJson,
            pinnedServerKeys = pinnedKeys,
            expectedType = "health",
            expectedNonce = nonce
        )

        assertTrue(result is EnvelopeVerifier.VerifyResult.Success)
        val success = result as EnvelopeVerifier.VerifyResult.Success
        assertEquals(true, success.payloadObj.getBoolean("ok"))
    }

    @Test
    fun testRejectUnknownServerKey() {
        val fakeServer = FakeServer()
        val envelopeJson = fakeServer.handleHealth("nonce_1")

        // Empty pinned keys
        val result = EnvelopeVerifier.verify(
            envelopeJsonStr = envelopeJson,
            pinnedServerKeys = emptyMap()
        )

        assertTrue(result is EnvelopeVerifier.VerifyResult.Error)
        val error = result as EnvelopeVerifier.VerifyResult.Error
        assertTrue(error.reason.contains("Unknown server key"))
    }

    @Test
    fun testRejectNonceMismatch() {
        val fakeServer = FakeServer()
        val envelopeJson = fakeServer.handleHealth("nonce_original")

        val pinnedKeys = mapOf(fakeServer.skid to fakeServer.serverKey)
        val result = EnvelopeVerifier.verify(
            envelopeJsonStr = envelopeJson,
            pinnedServerKeys = pinnedKeys,
            expectedNonce = "nonce_different"
        )

        assertTrue(result is EnvelopeVerifier.VerifyResult.Error)
        val error = result as EnvelopeVerifier.VerifyResult.Error
        assertTrue(error.reason.contains("nonce"))
    }
}
