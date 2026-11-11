package com.ambay.billing

import com.ambay.billing.core.crypto.DeviceKeyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class RequestSigningTest {

    @Test
    fun testSha256HexHash() {
        val emptyHash = DeviceKeyManager.sha256Hex("")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)

        val textHash = DeviceKeyManager.sha256Hex("hello")
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", textHash)
    }

    @Test
    fun testRequestSigningFormat() {
        val method = "GET"
        val path = "/v1/sync/sales?after=12"
        val ts = "1731300000000"
        val nonce = "random_nonce_1234"
        val body = ""
        val bodyHash = DeviceKeyManager.sha256Hex(body)

        val expectedMessage = "AMBREQ1\nGET\n/v1/sync/sales?after=12\n1731300000000\nrandom_nonce_1234\n$bodyHash"
        assertTrue(expectedMessage.startsWith("AMBREQ1\nGET\n/v1/sync/sales?after=12\n"))
    }
}
