package com.ambay.billing

import com.ambay.billing.core.AppSettings
import com.ambay.billing.core.BackupConverter
import com.ambay.billing.core.CustomerInfo
import com.ambay.billing.core.Item
import com.ambay.billing.core.ShopInfo
import com.ambay.billing.core.StockMove
import com.ambay.billing.core.WSale
import com.ambay.billing.core.WSaleLine
import com.ambay.billing.core.WSaleTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRoundTripTest {

    @Test
    fun `website schema 2 snapshot export and import are lossless`() {
        val items = listOf(
            Item(id = "it_1", name = "20W Adapter", model = "PD-20", code = "ITM001", price = 79900L, cost = 45000L, ws = "W", war = "6m", md = 10000L, tracked = true, qty = 10)
        )
        val wsales = listOf(
            WSale(
                id = "550e8400-e29b-41d4-a716-446655440001",
                no = "WB-0001",
                date = 1700000000000L,
                lines = listOf(WSaleLine("it_1", "20W Adapter", qty = 1, op = 79900L, gross = 79900L, disc = 9900L, total = 70000L, ws = "W", war = "6m")),
                tot = WSaleTotals(qty = 1, orig = 79900L, disc = 9900L, total = 70000L),
                cust = CustomerInfo("Rahul", "9876543210")
            )
        )
        val moves = listOf(
            StockMove(id = "opening:it_1", itemId = "it_1", type = "opening", qty = 11, ts = 1699990000000L),
            StockMove(id = "wsale:550e8400-e29b-41d4-a716-446655440001:0", itemId = "it_1", type = "sale", qty = -1, ts = 1700000000000L)
        )
        val exported = BackupConverter.exportSnapshotJson(
            devId = "dev_web",
            counter = 5,
            wsCounter = 2,
            shop = ShopInfo(),
            settings = AppSettings(),
            items = items,
            bills = emptyList(),
            moves = moves,
            cats = emptyList(),
            wsales = wsales
        )
        val parsed = BackupConverter.parseBackupJson(exported).getOrThrow()
        assertEquals("snapshot", parsed.kind)
        assertEquals(1, parsed.items.size)
        assertEquals(79900L, parsed.items[0].price)
        assertEquals(45000L, parsed.items[0].cost)
        assertEquals(1, parsed.wsales.size)
        assertEquals(70000L, parsed.wsales[0].tot.total)
        assertEquals(2, parsed.moves.size)
    }

    @Test
    fun `v1 legacy backup migrates cleanly`() {
        val v1 = """{"inv":[{"id":"1","name":"Earphone","code":"EP1","price":150,"qty":5}],"hist":[],"counter":3}"""
        val parsed = BackupConverter.parseBackupJson(v1).getOrThrow()
        assertEquals("legacy", parsed.kind)
        assertTrue(parsed.items.isNotEmpty())
        assertEquals(15000L, parsed.items[0].price)
    }
}
