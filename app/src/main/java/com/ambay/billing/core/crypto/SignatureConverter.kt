package com.ambay.billing.core.crypto

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Converts ECDSA P-256 signatures between ASN.1 DER sequence and raw IEEE P1363 (64 bytes: r[32] || s[32]).
 */
object SignatureConverter {

    /**
     * Converts an ASN.1 DER encoded ECDSA signature into a raw 64-byte array (r || s).
     */
    fun derToRaw(derSig: ByteArray): ByteArray {
        if (derSig.size < 8 || derSig[0] != 0x30.toByte()) {
            throw IllegalArgumentException("Invalid DER signature format: missing sequence tag")
        }

        var offset = 1
        var seqLen = derSig[offset++].toInt() and 0xFF
        if (seqLen and 0x80 != 0) {
            val bytesCount = seqLen and 0x7F
            seqLen = 0
            for (i in 0 until bytesCount) {
                seqLen = (seqLen shl 8) or (derSig[offset++].toInt() and 0xFF)
            }
        }

        // Read r
        if (derSig[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature: missing r integer tag")
        }
        val rLen = derSig[offset++].toInt() and 0xFF
        val rBytes = derSig.copyOfRange(offset, offset + rLen)
        offset += rLen

        // Read s
        if (derSig[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature: missing s integer tag")
        }
        val sLen = derSig[offset++].toInt() and 0xFF
        val sBytes = derSig.copyOfRange(offset, offset + sLen)

        // Normalize r and s to exactly 32 bytes each
        val raw = ByteArray(64)
        val normalizedR = stripLeadingZeros(rBytes)
        val normalizedS = stripLeadingZeros(sBytes)

        if (normalizedR.size > 32 || normalizedS.size > 32) {
            throw IllegalArgumentException("Invalid DER signature: component exceeds 32 bytes")
        }

        System.arraycopy(normalizedR, 0, raw, 32 - normalizedR.size, normalizedR.size)
        System.arraycopy(normalizedS, 0, raw, 64 - normalizedS.size, normalizedS.size)

        return raw
    }

    /**
     * Converts a raw 64-byte IEEE P1363 signature (r || s) into an ASN.1 DER sequence.
     */
    fun rawToDer(rawSig: ByteArray): ByteArray {
        if (rawSig.size != 64) {
            throw IllegalArgumentException("Raw signature must be exactly 64 bytes (found ${rawSig.size})")
        }

        val r = rawSig.copyOfRange(0, 32)
        val s = rawSig.copyOfRange(32, 64)

        val rDer = encodeDerInteger(stripLeadingZeros(r))
        val sDer = encodeDerInteger(stripLeadingZeros(s))

        val seqLen = rDer.size + sDer.size
        val baos = ByteArrayOutputStream()
        baos.write(0x30)
        encodeLength(baos, seqLen)
        baos.write(rDer)
        baos.write(sDer)

        return baos.toByteArray()
    }

    private fun stripLeadingZeros(bytes: ByteArray): ByteArray {
        var start = 0
        while (start < bytes.size - 1 && bytes[start] == 0.toByte()) {
            start++
        }
        return bytes.copyOfRange(start, bytes.size)
    }

    private fun encodeDerInteger(bytes: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(0x02)
        // If highest bit is 1, prepend 0x00 to mark as positive integer in ASN.1
        val pad = (bytes.isNotEmpty() && (bytes[0].toInt() and 0x80) != 0)
        val len = bytes.size + (if (pad) 1 else 0)
        encodeLength(baos, len)
        if (pad) {
            baos.write(0x00)
        }
        baos.write(bytes)
        return baos.toByteArray()
    }

    private fun encodeLength(baos: ByteArrayOutputStream, length: Int) {
        if (length < 128) {
            baos.write(length)
        } else {
            val lenBytes = mutableListOf<Byte>()
            var temp = length
            while (temp > 0) {
                lenBytes.add((temp and 0xFF).toByte())
                temp = temp ushr 8
            }
            baos.write(0x80 or lenBytes.size)
            for (i in lenBytes.indices.reversed()) {
                baos.write(lenBytes[i].toInt())
            }
        }
    }
}
