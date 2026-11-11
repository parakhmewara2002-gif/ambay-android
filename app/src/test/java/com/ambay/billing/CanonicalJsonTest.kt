package com.ambay.billing

import com.ambay.billing.core.crypto.CanonicalJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CanonicalJsonTest {

    @Test
    fun testBasicTypes() {
        assertEquals("null", CanonicalJson.canon(null))
        assertEquals("true", CanonicalJson.canon(true))
        assertEquals("false", CanonicalJson.canon(false))
        assertEquals("123", CanonicalJson.canon(123))
        assertEquals("-456", CanonicalJson.canon(-456L))
        assertEquals("\"hello\"", CanonicalJson.canon("hello"))
    }

    @Test
    fun testKeySortingAscendingUtf16() {
        val map = mapOf(
            "z" to 10,
            "a" to "hello",
            "b" to true,
            "c" to null
        )
        val expected = """{"a":"hello","b":true,"c":null,"z":10}"""
        assertEquals(expected, CanonicalJson.canon(map))
    }

    @Test
    fun testNestedObjectsAndArrays() {
        val nested = mapOf(
            "z" to 10,
            "sub" to mapOf(
                "y" to 2,
                "x" to listOf(1, 2, 3)
            ),
            "a" to "test"
        )
        val expected = """{"a":"test","sub":{"x":[1,2,3],"y":2},"z":10}"""
        assertEquals(expected, CanonicalJson.canon(nested))
    }

    @Test
    fun testStringEscaping() {
        val map = mapOf(
            "msg" to "hello \"world\"\nnew\tline"
        )
        val expected = """{"msg":"hello \"world\"\nnew\tline"}"""
        assertEquals(expected, CanonicalJson.canon(map))
    }

    @Test
    fun testFloatRejection() {
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalJson.canon(12.34)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalJson.canon(Double.NaN)
        }
    }
}
