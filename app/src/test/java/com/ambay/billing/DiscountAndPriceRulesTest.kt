package com.ambay.billing

import com.ambay.billing.core.model.Item
import com.ambay.billing.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountAndPriceRulesTest {

    @Test
    fun testMinSellingPriceCalculation() {
        // Item: Price ₹500 (50,000 paise), Max Discount ₹150 (15,000 paise)
        val pricePaise = 50_000L
        val maxDiscPaise = 15_000L

        val minPrice = Money.minSellingPrice(pricePaise, maxDiscPaise)
        assertEquals(35_000L, minPrice) // ₹350
    }

    @Test
    fun testZeroMaxDiscount() {
        // md = 0: no discount allowed
        val pricePaise = 20_000L
        val maxDiscPaise = 0L

        val minPrice = Money.minSellingPrice(pricePaise, maxDiscPaise)
        assertEquals(20_000L, minPrice)
        assertFalse(Money.isDiscountAllowed(pricePaise, maxDiscPaise, 100L))
        assertTrue(Money.isDiscountAllowed(pricePaise, maxDiscPaise, 0L))
    }

    @Test
    fun testUnrestrictedDiscount() {
        // md = null: unconstrained discount
        val pricePaise = 10_000L
        val maxDiscPaise: Long? = null

        val minPrice = Money.minSellingPrice(pricePaise, maxDiscPaise)
        assertEquals(0L, minPrice)
        assertTrue(Money.isDiscountAllowed(pricePaise, maxDiscPaise, 5_000L))
    }

    @Test
    fun testItemModelMaxDiscountHelper() {
        val item = Item(
            id = "item_1",
            name = "Fast Charger 33W",
            price = 69900L,
            md = 15000L,
            ws = "W"
        )

        assertEquals(15000L, item.maxDiscountPaise())
        assertEquals(54900L, item.minSellingPricePaise())
    }
}
