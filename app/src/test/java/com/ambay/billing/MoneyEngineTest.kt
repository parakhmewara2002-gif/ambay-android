package com.ambay.billing

import com.ambay.billing.core.CalcLineInput
import com.ambay.billing.core.CalcOptions
import com.ambay.billing.core.Item
import com.ambay.billing.core.MoneyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyEngineTest {

    @Test
    fun `toPaise and fmtINR handle integer paise without float drift`() {
        assertEquals(19999L, MoneyEngine.toPaise("199.99"))
        assertEquals(100000L, MoneyEngine.toPaise("1,000"))
        assertEquals("₹1,000.00", MoneyEngine.fmtINR(100000L))
        assertEquals("-₹50.25", MoneyEngine.fmtINR(-5025L))
    }

    @Test
    fun `allocate preserves exact integer total via largest remainder`() {
        val parts = MoneyEngine.allocate(100L, listOf(1L, 1L, 1L))
        assertEquals(listOf(34L, 33L, 33L), parts)
        assertEquals(100L, parts.sum())
    }

    @Test
    fun `calcBill exclusive and inclusive GST reconcile exactly`() {
        val exc = MoneyEngine.calcBill(
            lines = listOf(CalcLineInput(qty = 2, rate = 10000L, cost = 6000L, gstBp = 1800)),
            opts = CalcOptions(discType = "amt", discVal = 0L, gstBp = 1800, mode = "exc", taxType = "intra")
        )
        assertEquals(20000L, exc.sub)
        assertEquals(3600L, exc.tax)
        assertEquals(1800L, exc.cgst)
        assertEquals(1800L, exc.sgst)
        assertEquals(23600L, exc.total)
        assertEquals(8000L, exc.profit)

        val inc = MoneyEngine.calcBill(
            lines = listOf(CalcLineInput(qty = 1, rate = 11800L, cost = 7000L, gstBp = 1800)),
            opts = CalcOptions(mode = "inc", taxType = "inter")
        )
        assertEquals(10000L, inc.taxable)
        assertEquals(1800L, inc.igst)
        assertEquals(11800L, inc.total)
    }

    @Test
    fun `discProblems rejects discount above item maxDisc`() {
        val item = Item(id = "it1", name = "Tempered Glass", price = 20000L, md = 5000L)
        val problems = MoneyEngine.discProblems(
            items = mapOf("it1" to item),
            lines = listOf("it1" to 2),
            nets = listOf(25000L) // gross 40000, given 15000, allowed 10000
        )
        assertEquals(1, problems.size)
        assertEquals(10000L, problems[0].allowed)
        assertEquals(30000L, problems[0].minTotal)
    }

    @Test
    fun `matchCode is exact never fuzzy`() {
        val items = listOf(
            Item(id = "1", name = "Cable", code = "00123", fac = "8901234567890"),
            Item(id = "2", name = "Charger", code = "123")
        )
        assertEquals(listOf("1"), MoneyEngine.matchCode(items, "00123").map { it.id })
        assertEquals(listOf("1"), MoneyEngine.matchCode(items, " 8901234567890\n").map { it.id })
        assertTrue(MoneyEngine.matchCode(items, "0123").isEmpty())
    }

    @Test
    fun `imeiCheck validates 15 digits and Luhn check digit`() {
        assertTrue(MoneyEngine.imeiCheck("490154203237518").ok)
        assertFalse(MoneyEngine.imeiCheck("490154203237519").ok)
        assertFalse(MoneyEngine.imeiCheck("12345").ok)
    }
}
