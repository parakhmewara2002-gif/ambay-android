package com.ambay.billing.core.crypto

import java.text.Normalizer

/**
 * RFC 8785 style canonical JSON serializer.
 * Strict rules:
 * 1. Object keys sorted by UTF-16 code unit order ascending.
 * 2. No whitespace between tokens.
 * 3. Strings are NFC-normalized and escaped.
 * 4. Numbers must be integers (safe integers); floats and doubles are rejected.
 * 5. Booleans and null are literal.
 */
object CanonicalJson {

    fun canon(value: Any?): String {
        if (value == null) return "null"

        return when (value) {
            is String -> escapeString(Normalizer.normalize(value, Normalizer.Form.NFC))
            is Boolean -> if (value) "true" else "false"
            is Byte, is Short, is Int, is Long -> value.toString()
            is Number -> {
                // Reject non-integers or floating point
                val d = value.toDouble()
                if (d.isNaN() || d.isInfinite() || Math.floor(d) != d) {
                    throw IllegalArgumentException("canonical JSON: non-integer number: $value")
                }
                value.toLong().toString()
            }
            is List<*> -> {
                val items = value.map { canon(it) }
                "[" + items.joinToString(",") + "]"
            }
            is Array<*> -> {
                val items = value.map { canon(it) }
                "[" + items.joinToString(",") + "]"
            }
            is Map<*, *> -> {
                val sortedEntries = value.entries
                    .map { (k, v) ->
                        val keyStr = k?.toString() ?: throw IllegalArgumentException("canonical JSON: null key")
                        val normKey = Normalizer.normalize(keyStr, Normalizer.Form.NFC)
                        normKey to v
                    }
                    .sortedWith { a, b -> compareUtf16(a.first, b.first) }

                val parts = sortedEntries.map { (k, v) ->
                    escapeString(k) + ":" + canon(v)
                }
                "{" + parts.joinToString(",") + "}"
            }
            else -> throw IllegalArgumentException("canonical JSON: unsupported type: ${value::class.java.name}")
        }
    }

    private fun compareUtf16(a: String, b: String): Int {
        val len1 = a.length
        val len2 = b.length
        val lim = minOf(len1, len2)
        for (k in 0 until lim) {
            val c1 = a[k].code
            val c2 = b[k].code
            if (c1 != c2) {
                return c1 - c2
            }
        }
        return len1 - len2
    }

    private fun escapeString(s: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (i in s.indices) {
            val c = s[i]
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code in 0x00..0x1F) {
                        sb.append(String.format(java.util.Locale.US, "\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
