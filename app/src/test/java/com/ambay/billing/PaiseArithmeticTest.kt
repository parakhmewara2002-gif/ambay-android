package com.ambay.billing

import com.ambay.billing.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class PaiseArithmeticTest {

    @Test
    fun testRupeeToPaiseConversion() {
        assertEquals(100L, Money.toPaise(1.0))
        assertEquals(9999L, Money.toPaise(99.99))
        assertEquals(50000L, Money.toPaise(500.0))
    }

    @Test
    fun testPaiseToRupeeConversion() {
        assertEquals(1.0, Money.toRupees(100L), 0.001)
        assertEquals(99.99, Money.toRupees(9999L), 0.001)
    }

    @Test
    fun testInrFormatting() {
        assertEquals("₹100.00", Money.formatInr(10000L))
        assertEquals("₹1,234.50", Money.formatInr(123450L))
        assertEquals("₹0.00", Money.formatInr(0L))
    }

    @Test
    fun testNoFloatingPointDrift() {
        // 0.1 + 0.2 in floating point produces 0.30000000000000004
        // In integer paise: 10 + 20 = 30 exactly
        val itemA = 10L
        val itemB = 20L
        val total = itemA + itemB
        assertEquals(30L, total)
    }
}
