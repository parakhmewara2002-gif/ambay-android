package com.ambay.billing.core.model

data class Item(
    val id: String,
    val name: String,
    val sn: String = "",              // Short name
    val model: String = "",           // Variant / Model
    val code: String = "",            // In-house code
    val fac: String = "",             // Factory barcode (EAN-13, UPC, Code 128, etc.)
    val cat: String = "",             // Category
    val price: Long = 0L,             // Selling price in paise (integer)
    val gst: Int = 0,                 // Basis points (1800 = 18%)
    val md: Long? = null,             // Max discount in paise (null if no limit)
    val ws: String = "N",             // Warranty status: 'W' (Warranty) or 'N' (Without Warranty)
    val qty: Int = 0,                 // Stock quantity
    val tracked: Boolean = true,
    val deleted: Boolean = false
) {
    fun maxDiscountPaise(): Long? = md?.coerceAtMost(price)?.coerceAtLeast(0L)

    fun minSellingPricePaise(): Long {
        val maxDisc = maxDiscountPaise() ?: return 0L
        return (price - maxDisc).coerceAtLeast(0L)
    }

    fun matchesBarcode(scannedCode: String): Boolean {
        val trimmed = scannedCode.trim()
        if (trimmed.isEmpty()) return false
        return (code.isNotEmpty() && code.equals(trimmed, ignoreCase = false)) ||
               (fac.isNotEmpty() && fac.equals(trimmed, ignoreCase = false))
    }
}
