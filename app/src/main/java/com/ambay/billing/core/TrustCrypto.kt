package com.ambay.billing.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class InvoiceQrPayload(
    val v: Int,
    val u: String,
    val n: String,
    val k: String,
    val a: String,
    val d: String,
    val s: String
)

data class OfflineVerdict(
    val status: String,
    val label: String,
    val level: String, // "ok" | "warn" | "bad"
    val signature: String = "n/a",
    val content: String = "unavailable",
    val kid: String? = null,
    val keyTrust: String? = null,
    val uuid: String? = null,
    val no: String? = null,
    val notes: List<String> = emptyList(),
    val flags: List<String> = emptyList()
)

sealed class EnvelopeVerifyResult {
    data class Valid(val payload: JsonObject, val serverTs: Long, val type: String) : EnvelopeVerifyResult()
    data class Invalid(val reason: String) : EnvelopeVerifyResult()
}

object TrustCrypto {
    private val json = Json { ignoreUnknownKeys = true }
    private val ALGS = setOf("Ed25519", "ES256")
    private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val HEX64_RE = Regex("^[0-9a-f]{64}$")
    private val B64U_RE = Regex("^[A-Za-z0-9_-]{1,120}$")
    private val INV_QR_RE = Regex("^AMBINV(\\d{1,3})\\.([A-Za-z0-9_-]+)$")
    private val TOKEN_RE = Regex("^AMBM1\\.([A-Za-z0-9_-]+)$")
    private val TOKEN_SCAN_RE = Regex("AMBM1\\.[A-Za-z0-9_-]{20,}")
    private val PAIR_QR_RE = Regex("^AMBPAIR1\\.([A-Za-z0-9_-]+)$")

    const val SIG_DOMAIN = "AMBINV1\n"
    const val SRV_DOMAIN = "AMBSRV1\n"
    const val REQ_DOMAIN = "AMBREQ1\n"
    const val ENROLL_DOMAIN = "AMBENROLL1\n"

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun b64uEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun b64uDecode(str: String): ByteArray {
        require(str.matches(Regex("^[A-Za-z0-9_-]*$"))) { "bad base64url" }
        return Base64.getUrlDecoder().decode(str)
    }

    fun randomB64u(numBytes: Int = 16): String {
        val b = ByteArray(numBytes)
        SecureRandom().nextBytes(b)
        return b64uEncode(b)
    }

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Hex(bytes: ByteArray): String =
        sha256(bytes).joinToString("") { "%02x".format(it) }

    fun sha256Hex(text: String): String =
        sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun kidOf(spkiB64u: String): String {
        val digest = sha256(b64uDecode(spkiB64u))
        return b64uEncode(digest.copyOfRange(0, 12))
    }

    /**
     * Converts ASN.1 DER ECDSA signature to raw 64-byte (r || s) for ES256.
     */
    fun derToRaw64(der: ByteArray): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte()) { "Invalid DER sequence" }
        var offset = 2
        if ((der[1].toInt() and 0x80) != 0) {
            val lenBytes = der[1].toInt() and 0x7F
            offset = 2 + lenBytes
        }
        require(der[offset] == 0x02.toByte()) { "Invalid DER r tag" }
        val rLen = der[offset + 1].toInt() and 0xFF
        val rStart = offset + 2
        val rBytes = der.copyOfRange(rStart, rStart + rLen)

        val sTagOffset = rStart + rLen
        require(der[sTagOffset] == 0x02.toByte()) { "Invalid DER s tag" }
        val sLen = der[sTagOffset + 1].toInt() and 0xFF
        val sStart = sTagOffset + 2
        val sBytes = der.copyOfRange(sStart, sStart + sLen)

        val out = ByteArray(64)
        copyUnsignedFixed32(rBytes, out, 0)
        copyUnsignedFixed32(sBytes, out, 32)
        return out
    }

    private fun copyUnsignedFixed32(src: ByteArray, dst: ByteArray, dstOffset: Int) {
        var start = 0
        while (start < src.size - 1 && src[start] == 0.toByte()) start++
        val len = src.size - start
        require(len <= 32) { "ECDSA component > 32 bytes" }
        System.arraycopy(src, start, dst, dstOffset + (32 - len), len)
    }

    /**
     * Converts raw 64-byte (r || s) ECDSA signature to ASN.1 DER for JCA verification.
     */
    fun raw64ToDer(raw64: ByteArray): ByteArray {
        require(raw64.size == 64) { "Expected 64-byte raw signature" }
        val r = encodeDerInt(raw64.copyOfRange(0, 32))
        val s = encodeDerInt(raw64.copyOfRange(32, 64))
        val seqLen = 2 + r.size + 2 + s.size
        val out = ByteArray(2 + seqLen)
        out[0] = 0x30
        out[1] = seqLen.toByte()
        var pos = 2
        out[pos++] = 0x02
        out[pos++] = r.size.toByte()
        System.arraycopy(r, 0, out, pos, r.size)
        pos += r.size
        out[pos++] = 0x02
        out[pos++] = s.size.toByte()
        System.arraycopy(s, 0, out, pos, s.size)
        return out
    }

    private fun encodeDerInt(bytes: ByteArray): ByteArray {
        var start = 0
        while (start < bytes.size - 1 && bytes[start] == 0.toByte()) start++
        val trimmed = bytes.copyOfRange(start, bytes.size)
        return if ((trimmed[0].toInt() and 0x80) != 0) {
            byteArrayOf(0x00) + trimmed
        } else {
            trimmed
        }
    }

    fun verifyBytes(alg: String, spkiB64u: String, message: ByteArray, sigB64u: String): Boolean {
        if (alg !in ALGS) return false
        return try {
            val sigBytes = b64uDecode(sigB64u)
            if (sigBytes.size != 64) return false
            val spkiBytes = b64uDecode(spkiB64u)
            val spec = X509EncodedKeySpec(spkiBytes)
            if (alg == "Ed25519") {
                val pub: PublicKey = KeyFactory.getInstance("Ed25519", "BC").generatePublic(spec)
                val verifier = Signature.getInstance("Ed25519", "BC")
                verifier.initVerify(pub)
                verifier.update(message)
                verifier.verify(sigBytes)
            } else {
                val pub: PublicKey = KeyFactory.getInstance("EC").generatePublic(spec)
                val verifier = Signature.getInstance("SHA256withECDSA")
                verifier.initVerify(pub)
                verifier.update(message)
                verifier.verify(raw64ToDer(sigBytes))
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun buildManifestMap(bill: Bill, kid: String, tz: String): Map<String, Any?> {
        val linesList = bill.lines.map { l ->
            val m = linkedMapOf<String, Any?>(
                "id" to (l.itemId ?: ""),
                "name" to l.name,
                "model" to l.model,
                "qty" to l.qty,
                "rate" to l.rate,
                "war" to l.war,
                "swar" to l.swar,
                "gstBp" to l.gstBp,
                "gross" to l.gross,
                "disc" to l.disc,
                "net" to l.net,
                "taxable" to l.taxable,
                "tax" to l.tax,
                "total" to l.total
            )
            if (l.ph != null) {
                m["ph"] = mapOf(
                    "c" to if (l.ph.cond == "old") "old" else "new",
                    "b" to l.ph.brand,
                    "r" to l.ph.ram,
                    "s" to l.ph.st,
                    "o" to l.ph.col,
                    "i1" to l.ph.imei1,
                    "i2" to l.ph.imei2,
                    "sn" to l.ph.sn,
                    "ob" to l.ph.ob,
                    "ac" to l.ph.acc,
                    "bc" to l.ph.bc
                )
            }
            m
        }
        return mapOf(
            "v" to 1,
            "t" to "amb.invoice",
            "uuid" to bill.id,
            "no" to bill.no,
            "issuedAt" to bill.date,
            "createdAt" to bill.createdAt,
            "tz" to tz,
            "kid" to kid,
            "dev" to bill.devId,
            "shop" to mapOf("name" to bill.shop.name, "gst" to bill.shop.gst),
            "pay" to bill.pay,
            "gstMode" to bill.gstMode,
            "taxType" to bill.taxType,
            "gstBp" to bill.gstBp,
            "disc" to mapOf("type" to bill.disc.type, "val" to bill.disc.valPaiseOrBp),
            "lines" to linesList,
            "tot" to mapOf(
                "sub" to bill.tot.sub,
                "disc" to bill.tot.disc,
                "taxable" to bill.tot.taxable,
                "tax" to bill.tot.tax,
                "cgst" to bill.tot.cgst,
                "sgst" to bill.tot.sgst,
                "igst" to bill.tot.igst,
                "total" to bill.tot.total
            )
        )
    }

    fun manifestDigest(manifest: Map<String, Any?>): String =
        sha256Hex(CanonicalJson.stringify(manifest))

    fun sigMessage(digestHex: String): ByteArray =
        (SIG_DOMAIN + digestHex).toByteArray(Charsets.UTF_8)

    fun reqMessage(method: String, pathAndQuery: String, bodyStr: String, ts: String, nonce: String): ByteArray {
        val bh = sha256Hex(bodyStr)
        return (REQ_DOMAIN + listOf(method.uppercase(), pathAndQuery, ts, nonce, bh).joinToString("\n"))
            .toByteArray(Charsets.UTF_8)
    }

    fun enrollMessage(code: String, kid: String, nonce: String): ByteArray =
        (ENROLL_DOMAIN + listOf(sha256Hex(code), kid, nonce).joinToString("\n"))
            .toByteArray(Charsets.UTF_8)

    fun encodePayload(bill: Bill): String {
        val ds = requireNotNull(bill.dsig) { "Bill is not signed" }
        val payloadJson = CanonicalJson.stringify(
            mapOf(
                "v" to 1,
                "u" to bill.id,
                "n" to bill.no,
                "k" to ds.kid,
                "a" to ds.alg,
                "d" to ds.dg,
                "s" to ds.sig
            )
        )
        return "AMBINV1." + b64uEncode(payloadJson.toByteArray(Charsets.UTF_8))
    }

    fun decodePayload(text: String): InvoiceQrPayload? {
        if (text.length > 4096) return null
        val m = INV_QR_RE.matchEntire(text.trim()) ?: return null
        if (m.groupValues[1].toIntOrNull() != 1) return null
        return try {
            val raw = b64uDecode(m.groupValues[2]).toString(Charsets.UTF_8)
            val obj = json.parseToJsonElement(raw).jsonObject
            if (obj.keys.sorted().joinToString(",") != "a,d,k,n,s,u,v") return null
            val v = obj["v"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null
            val u = obj["u"]?.jsonPrimitive?.content ?: return null
            val n = obj["n"]?.jsonPrimitive?.content ?: return null
            val k = obj["k"]?.jsonPrimitive?.content ?: return null
            val a = obj["a"]?.jsonPrimitive?.content ?: return null
            val d = obj["d"]?.jsonPrimitive?.content ?: return null
            val s = obj["s"]?.jsonPrimitive?.content ?: return null
            if (v != 1 || !UUID_RE.matches(u) || n.length > 40 || !B64U_RE.matches(k) ||
                a !in ALGS || !HEX64_RE.matches(d) || !B64U_RE.matches(s)
            ) return null
            InvoiceQrPayload(v, u, n, k, a, d, s)
        } catch (_: Throwable) {
            null
        }
    }

    fun makeToken(manifest: Map<String, Any?>, dsig: DigitalSig): String {
        val mStr = CanonicalJson.stringify(manifest)
        val wrapper = CanonicalJson.stringify(
            mapOf(
                "m" to mStr,
                "s" to mapOf("alg" to dsig.alg, "kid" to dsig.kid, "dg" to dsig.dg, "sig" to dsig.sig)
            )
        )
        return "AMBM1." + b64uEncode(wrapper.toByteArray(Charsets.UTF_8))
    }

    fun extractTokensFromPdf(bytes: ByteArray): List<String> {
        if (bytes.size > 15 * 1024 * 1024 || bytes.size < 5) return emptyList()
        val header = String(bytes, 0, 5, Charsets.US_ASCII)
        if (header != "%PDF-") return emptyList()
        val text = String(bytes, Charsets.ISO_8859_1)
        return TOKEN_SCAN_RE.findAll(text).map { it.value }.distinct().take(5).toList()
    }

    fun verifyOffline(
        payload: InvoiceQrPayload? = null,
        token: String? = null,
        bill: Bill? = null,
        trustedKeys: Map<String, TrustedKeyEntry> = emptyMap(),
        localKeys: Map<String, TrustedKeyEntry> = emptyMap()
    ): OfflineVerdict {
        val notes = mutableListOf<String>()
        var sigAlg: String? = payload?.a
        var sigKid: String? = payload?.k
        var sigDg: String? = payload?.d
        var sigStr: String? = payload?.s
        var canonicalManifest: String? = null
        var manifestObj: JsonObject? = null

        if (token != null) {
            val tm = TOKEN_RE.matchEntire(token.trim())
                ?: return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn", notes = listOf("not a manifest token"))
            try {
                val raw = b64uDecode(tm.groupValues[1]).toString(Charsets.UTF_8)
                val wrapper = json.parseToJsonElement(raw).jsonObject
                val mStr = wrapper["m"]?.jsonPrimitive?.content ?: return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn")
                val sObj = wrapper["s"]?.jsonObject ?: return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn")
                val mParsed = json.parseToJsonElement(mStr).jsonObject
                if (mParsed["t"]?.jsonPrimitive?.content != "amb.invoice") {
                    return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn", notes = listOf("not an invoice manifest"))
                }
                val tAlg = sObj["alg"]?.jsonPrimitive?.content ?: ""
                val tKid = sObj["kid"]?.jsonPrimitive?.content ?: ""
                val tDg = sObj["dg"]?.jsonPrimitive?.content ?: ""
                val tSig = sObj["sig"]?.jsonPrimitive?.content ?: ""
                if (sigStr != null && (sigStr != tSig || sigDg != tDg || sigKid != tKid)) {
                    return OfflineVerdict("CONTENT_MODIFIED", "BILL CONTENT MODIFIED", "bad", signature = "unknown", content = "modified", notes = listOf("QR code and embedded manifest belong to different signatures"))
                }
                sigAlg = tAlg; sigKid = tKid; sigDg = tDg; sigStr = tSig
                canonicalManifest = mStr
                manifestObj = mParsed
            } catch (_: Throwable) {
                return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn", notes = listOf("unreadable manifest token"))
            }
        }

        if (bill != null) {
            val ds = bill.dsig ?: return OfflineVerdict(
                "UNSIGNED",
                "UNSIGNED — issued before signing was enabled",
                "warn",
                notes = listOf("This bill has no digital signature.")
            )
            val localMap = buildManifestMap(bill, ds.kid, ds.tz)
            val localCanon = CanonicalJson.stringify(localMap)
            if (sigStr != null && (sigStr != ds.sig || sigDg != ds.dg)) {
                return OfflineVerdict("CONTENT_MODIFIED", "BILL CONTENT MODIFIED", "bad", signature = "unknown", content = "modified", notes = listOf("Stored bill does not match the scanned signature"))
            }
            if (canonicalManifest == null) {
                canonicalManifest = localCanon
                manifestObj = json.parseToJsonElement(localCanon).jsonObject
            }
            if (sigStr == null) {
                sigAlg = ds.alg; sigKid = ds.kid; sigDg = ds.dg; sigStr = ds.sig
            }
        }

        val alg = sigAlg ?: return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn", notes = listOf("nothing to verify"))
        val kid = sigKid ?: return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn")
        val dg = sigDg ?: return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn")
        val sig = sigStr ?: return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn")

        if (alg !in ALGS) return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn", notes = listOf("unsupported signature algorithm"))
        if (!HEX64_RE.matches(dg)) return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn", notes = listOf("malformed digest"))

        val key = localKeys[kid]?.let { lk ->
            val remote = trustedKeys[kid]
            if (remote?.status == "revoked") lk.copy(status = "revoked", revokedAt = remote.revokedAt) else lk
        } ?: trustedKeys[kid]
        ?: return OfflineVerdict(
            "UNTRUSTED_KEY",
            "UNKNOWN OR UNTRUSTED SIGNING KEY",
            "warn",
            signature = "unknown",
            keyTrust = "untrusted",
            kid = kid,
            notes = listOf("Signing key $kid is not in this device's trust list.")
        )

        if (key.alg != alg) {
            return OfflineVerdict("INVALID_SIGNATURE", "INVALID SIGNATURE", "bad", signature = "invalid", notes = listOf("algorithm mismatch"))
        }
        if (!verifyBytes(alg, key.spki, sigMessage(dg), sig)) {
            return OfflineVerdict("INVALID_SIGNATURE", "INVALID SIGNATURE", "bad", signature = "invalid", kid = kid, notes = listOf("Signature does not match the signing key"))
        }

        val uuid = payload?.u ?: manifestObj?.get("uuid")?.jsonPrimitive?.content
        val no = payload?.n ?: manifestObj?.get("no")?.jsonPrimitive?.content
        val keyTrust = if (key.status == "revoked") "revoked" else "trusted"

        if (canonicalManifest != null && manifestObj != null) {
            val dgNow = try {
                if (CanonicalJson.stringifyElement(manifestObj) != canonicalManifest && token != null) "noncanonical"
                else sha256Hex(canonicalManifest)
            } catch (_: Throwable) {
                return OfflineVerdict("INCOMPLETE", "VERIFICATION INCOMPLETE", "warn", signature = "valid", kid = kid, keyTrust = keyTrust)
            }
            if (dgNow != dg) {
                return OfflineVerdict("CONTENT_MODIFIED", "BILL CONTENT MODIFIED", "bad", signature = "valid", content = "modified", kid = kid, keyTrust = keyTrust, uuid = uuid, no = no, notes = listOf("Bill details no longer match what was signed"))
            }
            if (payload != null && (manifestObj["uuid"]?.jsonPrimitive?.content != payload.u || manifestObj["no"]?.jsonPrimitive?.content != payload.n)) {
                return OfflineVerdict("CONTENT_MODIFIED", "BILL CONTENT MODIFIED", "bad", signature = "valid", content = "modified", kid = kid, keyTrust = keyTrust, uuid = uuid, no = no, notes = listOf("Bill number/ID differs from signed manifest"))
            }
            if (key.status == "revoked") {
                return OfflineVerdict("KEY_REVOKED", "SIGNING KEY REVOKED", "bad", signature = "valid", content = "match", kid = kid, keyTrust = "revoked", uuid = uuid, no = no, notes = listOf("Signing key was revoked"))
            }
            return OfflineVerdict(
                "SIGNATURE_VALID_CONTENT_MATCHES",
                "SIGNATURE VALID — CONTENT MATCHES",
                "ok",
                signature = "valid",
                content = "match",
                kid = kid,
                keyTrust = keyTrust,
                uuid = uuid,
                no = no,
                notes = notes + "Signature verified with a trusted key and the signed details match.",
                flags = listOf("OFFLINE_STATUS_UNKNOWN", "SERVER_REGISTRATION_NOT_CONFIRMED")
            )
        }

        return OfflineVerdict(
            "INCOMPLETE",
            "VERIFICATION INCOMPLETE",
            "warn",
            signature = "valid",
            content = "unavailable",
            kid = kid,
            keyTrust = keyTrust,
            uuid = uuid,
            no = no,
            notes = listOf("Signature is valid for bill $no, but full bill details are not on this device."),
            flags = listOf("OFFLINE_STATUS_UNKNOWN", "SERVER_REGISTRATION_NOT_CONFIRMED")
        )
    }

    fun verifyEnvelope(
        env: ServerEnvelope,
        serverKeys: Map<String, ServerKeyEntry>,
        expectedType: String? = null,
        expectedNonce: String? = null
    ): EnvelopeVerifyResult {
        val k = serverKeys[env.skid] ?: return EnvelopeVerifyResult.Invalid("unknown server key")
        if (k.alg !in ALGS) return EnvelopeVerifyResult.Invalid("unsupported server algorithm")
        if (env.alg != null && env.alg != k.alg) return EnvelopeVerifyResult.Invalid("server algorithm mismatch")

        val canonicalPayload = try {
            CanonicalJson.stringifyElement(env.p)
        } catch (e: Throwable) {
            return EnvelopeVerifyResult.Invalid("non-canonical server payload: ${e.message}")
        }
        val msg = (SRV_DOMAIN + canonicalPayload).toByteArray(Charsets.UTF_8)
        if (!verifyBytes(k.alg, k.spki, msg, env.sig)) {
            return EnvelopeVerifyResult.Invalid("server signature invalid")
        }

        val type = (env.p["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return EnvelopeVerifyResult.Invalid("missing response type")
        if (expectedType != null && type != expectedType) {
            return EnvelopeVerifyResult.Invalid("unexpected response type: $type")
        }

        val nonce = (env.p["nonce"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (expectedNonce != null && nonce != expectedNonce) {
            return EnvelopeVerifyResult.Invalid("replayed or mismatched response (nonce)")
        }

        val serverTs = (env.p["serverTs"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            ?: return EnvelopeVerifyResult.Invalid("missing or non-integer serverTs")

        return EnvelopeVerifyResult.Valid(env.p, serverTs, type)
    }

    fun parsePairingQr(qrText: String): PairingQrPayload? {
        val m = PAIR_QR_RE.matchEntire(qrText.trim()) ?: return null
        return try {
            val raw = b64uDecode(m.groupValues[1]).toString(Charsets.UTF_8)
            val parsed = json.decodeFromString<PairingQrPayload>(raw)
            if (parsed.v != 1 || parsed.u.isBlank() || parsed.t.length < 32 || parsed.r.spki.isBlank()) null
            else parsed
        } catch (_: Throwable) {
            null
        }
    }
}
