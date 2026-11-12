package com.ambay.billing

import com.ambay.billing.core.Bill
import com.ambay.billing.core.BillLine
import com.ambay.billing.core.BillTotals
import com.ambay.billing.core.CanonicalJson
import com.ambay.billing.core.DigitalSig
import com.ambay.billing.core.TrustCrypto
import com.ambay.billing.core.TrustedKeyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class CanonicalJsonAndCryptoTest {

    @Test
    fun `canonicalJson sorts keys recursively with no spaces`() {
        val input = mapOf("b" to 2, "a" to mapOf("z" to 1, "m" to "hi"))
        assertEquals("""{"a":{"m":"hi","z":1},"b":2}""", CanonicalJson.stringify(input))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `canonicalJson rejects floats`() {
        CanonicalJson.stringify(mapOf("x" to 12.5))
    }

    @Test
    fun `DER to raw64 and back verifies ES256 signature`() {
        val kp = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val spki = TrustCrypto.b64uEncode(kp.public.encoded)
        val msg = "AMBREQ1\nPOST\n/v1/sync/sales\n1700000000000\nabcdef12\n00".toByteArray()
        val der = Signature.getInstance("SHA256withECDSA").apply {
            initSign(kp.private)
            update(msg)
        }.sign()
        val raw64 = TrustCrypto.derToRaw64(der)
        assertEquals(64, raw64.size)
        assertTrue(TrustCrypto.verifyBytes("ES256", spki, msg, TrustCrypto.b64uEncode(raw64)))
    }

    @Test
    fun `bill manifest signing, QR payload, and tamper detection work end-to-end`() {
        val kp = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val spki = TrustCrypto.b64uEncode(kp.public.encoded)
        val kid = TrustCrypto.kidOf(spki)

        val bill = Bill(
            id = "550e8400-e29b-41d4-a716-446655440000",
            no = "AMB-0001",
            date = 1700000000000L,
            lines = listOf(BillLine(itemId = "it1", name = "Cover", qty = 1, rate = 19900L, gross = 19900L, net = 19900L, taxable = 19900L, total = 19900L)),
            tot = BillTotals(sub = 19900L, taxable = 19900L, total = 19900L),
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )
        val manifest = TrustCrypto.buildManifestMap(bill, kid, "Asia/Kolkata")
        val dg = TrustCrypto.manifestDigest(manifest)
        val der = Signature.getInstance("SHA256withECDSA").apply {
            initSign(kp.private)
            update(TrustCrypto.sigMessage(dg))
        }.sign()
        val sig = TrustCrypto.b64uEncode(TrustCrypto.derToRaw64(der))
        val signedBill = bill.copy(dsig = DigitalSig(1, "ES256", kid, dg, sig, 1700000000000L, "Asia/Kolkata"))

        val qr = TrustCrypto.encodePayload(signedBill)
        val decoded = TrustCrypto.decodePayload(qr)
        assertNotNull(decoded)

        val localKeys = mapOf(kid to TrustedKeyEntry("ES256", spki, "active"))
        val validVerdict = TrustCrypto.verifyOffline(payload = decoded, bill = signedBill, localKeys = localKeys)
        assertEquals("SIGNATURE_VALID_CONTENT_MATCHES", validVerdict.status)

        // Tamper total
        val tamperedBill = signedBill.copy(tot = signedBill.tot.copy(total = 9900L))
        val tamperedVerdict = TrustCrypto.verifyOffline(payload = decoded, bill = tamperedBill, localKeys = localKeys)
        assertEquals("CONTENT_MODIFIED", tamperedVerdict.status)
    }
}
