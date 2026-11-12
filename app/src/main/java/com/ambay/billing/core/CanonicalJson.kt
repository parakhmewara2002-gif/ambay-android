package com.ambay.billing.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer

/**
 * RFC 8785-style canonical JSON serialisation matching js/trust.js `canon(v)`:
 * - Object keys sorted by UTF-16 code unit
 * - No whitespace
 * - Strings NFC-normalised and JSON-escaped
 * - Numbers MUST be safe integers (within JS Number.MAX_SAFE_INTEGER)
 */
object CanonicalJson {
    private const val MAX_SAFE_INT = 9_007_199_254_740_991L

    fun stringify(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> if (v) "true" else "false"
        is Int -> v.toString()
        is Long -> {
            require(v in -MAX_SAFE_INT..MAX_SAFE_INT) { "canonical JSON: non-safe integer $v" }
            v.toString()
        }
        is String -> escapeJsonString(Normalizer.normalize(v, Normalizer.Form.NFC))
        is List<*> -> v.joinToString(",", "[", "]") { stringify(it) }
        is Map<*, *> -> {
            val entries = v.entries
                .map { (k, valObj) ->
                    require(k is String) { "canonical JSON: non-string key" }
                    k to valObj
                }
                .sortedWith { a, b -> compareUtf16(a.first, b.first) }
            entries.joinToString(",", "{", "}") { (k, valObj) ->
                escapeJsonString(Normalizer.normalize(k, Normalizer.Form.NFC)) + ":" + stringify(valObj)
            }
        }
        is JsonElement -> stringifyElement(v)
        else -> throw IllegalArgumentException("canonical JSON: unsupported type ${v::class.java.simpleName}")
    }

    fun stringifyElement(el: JsonElement): String = when (el) {
        is JsonNull -> "null"
        is JsonPrimitive -> {
            if (el.isString) {
                escapeJsonString(Normalizer.normalize(el.content, Normalizer.Form.NFC))
            } else {
                val content = el.content
                when (content) {
                    "true", "false" -> content
                    else -> {
                        val longVal = content.toLongOrNull()
                            ?: throw IllegalArgumentException("canonical JSON: non-integer number $content")
                        require(longVal in -MAX_SAFE_INT..MAX_SAFE_INT) {
                            "canonical JSON: non-safe integer $longVal"
                        }
                        longVal.toString()
                    }
                }
            }
        }
        is JsonArray -> el.joinToString(",", "[", "]") { stringifyElement(it) }
        is JsonObject -> el.entries
            .sortedWith { a, b -> compareUtf16(a.key, b.key) }
            .joinToString(",", "{", "}") { (k, v) ->
                escapeJsonString(Normalizer.normalize(k, Normalizer.Form.NFC)) + ":" + stringifyElement(v)
            }
    }

    private fun compareUtf16(a: String, b: String): Int {
        val len = minOf(a.length, b.length)
        for (i in 0 until len) {
            val diff = a[i].code - b[i].code
            if (diff != 0) return diff
        }
        return a.length - b.length
    }

    private fun escapeJsonString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
