package com.ambay.billing

import com.ambay.billing.core.crypto.SignatureConverter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class SignatureConverterTest {

    @Test
    fun testDerToRawConversionLength() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()

        val data = "test signature conversion data".toByteArray(Charsets.UTF_8)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(data)
        val derSig = signer.sign()

        val rawSig = SignatureConverter.derToRaw(derSig)
        assertEquals(64, rawSig.size)

        // Convert back to DER and verify with standard verifier
        val recreatedDer = SignatureConverter.rawToDer(rawSig)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keyPair.public)
        verifier.update(data)
        val verified = verifier.verify(recreatedDer)
        assertEquals(true, verified)
    }

    @Test
    fun testRoundTripWithLeadingZeros() {
        val raw = ByteArray(64)
        // Set r = 1, s = 2
        raw[31] = 1
        raw[63] = 2

        val der = SignatureConverter.rawToDer(raw)
        val rawBack = SignatureConverter.derToRaw(der)
        assertArrayEquals(raw, rawBack)
    }
}
