package com.ambay.billing.core.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money calculations in integer paise.
 * 1 Rupee = 100 Paise.
 * Prevents floating-point rounding errors in all financial computations.
 */
object Money {
    private val inrFormatter = DecimalFormat("##,##,##0.00", DecimalFormatSymbols(Locale("en", "IN")))

    fun formatInr(paise: Long): String {
        val rupees = paise.toDouble() / 100.0
        return "₹" + inrFormatter.format(rupees)
    }

    fun toPaise(rupees: Double): Long {
        return Math.round(rupees * 100.0)
    }

    fun toRupees(paise: Long): Double {
        return paise.toDouble() / 100.0
    }

    /**
     * Calculates minimum selling price based on original price and max discount.
     * minPrice = max(0, price - maxDiscount)
     */
    fun minSellingPrice(pricePaise: Long, maxDiscountPaise: Long?): Long {
        if (maxDiscountPaise == null || maxDiscountPaise < 0) return 0L
        val allowedDiscount = maxDiscountPaise.coerceAtMost(pricePaise)
        return (pricePaise - allowedDiscount).coerceAtLeast(0L)
    }

    /**
     * Checks if given discount is within allowed item limit.
     */
    fun isDiscountAllowed(pricePaise: Long, maxDiscountPaise: Long?, givenDiscountPaise: Long): Boolean {
        if (givenDiscountPaise <= 0) return true
        if (maxDiscountPaise == null) return true // No limit configured
        val allowed = maxDiscountPaise.coerceAtMost(pricePaise)
        return givenDiscountPaise <= allowed
    }
}
