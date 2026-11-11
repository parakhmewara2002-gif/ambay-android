package com.ambay.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdempotentSaleAndStockTest {

    @Test
    fun testMovementIdUniqueness() {
        val txnId = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        val lineIndex = 0

        val saleMovementId = "wsale:$txnId:$lineIndex"
        val voidMovementId = "wsv:$txnId:$lineIndex"

        assertEquals("wsale:6ba7b810-9dad-11d1-80b4-00c04fd430c8:0", saleMovementId)
        assertEquals("wsv:6ba7b810-9dad-11d1-80b4-00c04fd430c8:0", voidMovementId)
        assertTrue(saleMovementId != voidMovementId)
    }

    @Test
    fun testStockDeductionIdempotencyLogic() {
        val movements = mutableSetOf<String>()
        var stock = 10
        val qtyToDeduct = 2
        val txnId = "uuid-1234"

        fun deductStock(lineIdx: Int) {
            val movementId = "wsale:$txnId:$lineIdx"
            if (!movements.contains(movementId)) {
                movements.add(movementId)
                stock -= qtyToDeduct
            }
        }

        // First attempt: deducts stock
        deductStock(0)
        assertEquals(8, stock)

        // Second attempt with same transaction ID and line index (idempotent replay): no change
        deductStock(0)
        assertEquals(8, stock)
    }
}
