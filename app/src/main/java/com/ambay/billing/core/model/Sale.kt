package com.ambay.billing.core.model

data class Sale(
    val id: String,                    // UUID v4 permanent transaction id
    val no: String,                    // Visible number e.g. "WB-0001"
    val type: String = "without_bill", // Always "without_bill" for staff mode
    val date: Long,                    // Epoch ms
    val lines: List<SaleLine>,
    val tot: SaleTotal,
    val pay: String = "Cash",          // "Cash" | "UPI" | "Card" | "Credit"
    val cust: Customer = Customer(),
    val note: String = "",
    val dev: String = "Android Device",
    val did: String = "",
    val createdAt: Long = date,
    val updatedAt: Long = date,
    val ver: Int = 1,
    val void: VoidInfo? = null,
    val syncStatus: String = "pending" // "pending" | "synced" | "conflict"
)

data class SaleLine(
    val itemId: String,
    val name: String,
    val sn: String = "",
    val model: String = "",
    val qty: Int,
    val op: Long,                      // Original unit list price in paise
    val gross: Long,                   // op * qty in paise
    val disc: Long,                    // gross - total in paise
    val total: Long,                   // Final selling price in paise
    val ws: String = "N",              // 'W' | 'N'
    val war: String = ""               // Warranty description
)

data class SaleTotal(
    val qty: Int,
    val orig: Long,                    // Sum of gross in paise
    val disc: Long,                    // Sum of line discounts in paise
    val total: Long                    // Final total in paise
)

data class Customer(
    val name: String = "",
    val phone: String = ""
)

data class VoidInfo(
    val ts: Long,
    val reason: String,
    val id: String = ""
)
