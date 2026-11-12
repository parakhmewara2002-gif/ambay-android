package com.ambay.billing.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Immutable
@Serializable
data class SyncMeta(
    val state: String = "pending", // pending | synced | failed
    val fileId: String? = null,
    val syncedVer: Int = 0,
    val at: Long = 0L,
    val err: String? = null
)

@Immutable
@Serializable
data class Item(
    val id: String,
    val name: String,
    val sn: String = "",
    val model: String = "",
    val code: String = "",
    val fac: String = "",
    val cat: String = "Uncategorized",
    val desc: String = "",
    val price: Long = 0L,        // paise
    val cost: Long = 0L,         // paise (never exposed to Staff)
    val gst: Int? = null,        // basis points (e.g. 1800 = 18%)
    val ws: String = "N",        // "W" | "N"
    val war: String = "",        // e.g. "6m", "1y"
    val swar: String = "",       // service centre warranty
    val md: Long? = null,        // max discount per unit (paise), null = no limit
    val rl: Int? = null,         // reorder level
    val rq: Int? = null,         // reorder qty
    val ser: Boolean = false,    // serial / IMEI tracked
    val tracked: Boolean = false,
    val qty: Int? = null,
    val deleted: Boolean = false,
    val deletedAt: Long = 0L,
    val restoredAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val fv: Int = 1,
    val rev: Int = 1,
    val devId: String = "",
    val sync: SyncMeta = SyncMeta()
)

@Immutable
@Serializable
data class CustomerInfo(
    val name: String = "",
    val phone: String = ""
)

@Immutable
@Serializable
data class PhoneDetails(
    val cond: String = "new", // "new" | "old"
    val brand: String = "",
    val ram: String = "",
    val st: String = "",
    val col: String = "",
    val imei1: String = "",
    val imei2: String = "",
    val sn: String = "",
    val ob: Boolean = false,
    val acc: List<String> = emptyList(),
    val bc: Boolean = false
)

@Immutable
@Serializable
data class BillLine(
    val itemId: String? = null,
    val name: String,
    val sn: String = "",
    val model: String = "",
    val qty: Int,
    val rate: Long,     // paise
    val cost: Long = 0L,
    val op: Long? = null,
    val ws: String = "N",
    val war: String = "",
    val swar: String = "",
    val gstBp: Int = 0,
    val gross: Long = 0L,
    val disc: Long = 0L,
    val net: Long = 0L,
    val taxable: Long = 0L,
    val tax: Long = 0L,
    val total: Long = 0L,
    val ph: PhoneDetails? = null
)

@Immutable
@Serializable
data class BillDiscount(
    val type: String = "amt", // "amt" | "pct"
    val valPaiseOrBp: Long = 0L
)

@Immutable
@Serializable
data class BillTotals(
    val sub: Long = 0L,
    val disc: Long = 0L,
    val taxable: Long = 0L,
    val tax: Long = 0L,
    val cgst: Long = 0L,
    val sgst: Long = 0L,
    val igst: Long = 0L,
    val total: Long = 0L,
    val cost: Long = 0L,
    val profit: Long = 0L
)

@Immutable
@Serializable
data class BillReturnLine(
    val idx: Int,
    val qty: Int,
    val amt: Long,
    val taxable: Long,
    val tax: Long,
    val cost: Long
)

@Immutable
@Serializable
data class BillReturn(
    val id: String,
    val date: Long,
    val lines: List<BillReturnLine>,
    val amt: Long,
    val taxable: Long,
    val tax: Long,
    val cost: Long,
    val reason: String = "",
    val cancel: Boolean = false
)

@Immutable
@Serializable
data class DigitalSig(
    val v: Int = 1,
    val alg: String, // "ES256" | "Ed25519"
    val kid: String,
    val dg: String,
    val sig: String,
    val ts: Long = 0L,
    val tz: String = ""
)

@Immutable
@Serializable
data class SigSnapshot(
    val enabled: Boolean = false,
    val name: String = "",
    val designation: String = "",
    val business: String = "",
    val label: String = "Authorized Signatory",
    val align: String = "right",
    val imgRef: String = "",
    val stampRef: String = ""
)

@Immutable
@Serializable
data class ShopInfo(
    val name: String = "AMBAY MOBILE ACCESSORIES",
    val ph: String = "7597244576, 7615036341",
    val addr: String = "",
    val gst: String = "",
    val foot: String = "Thank you! Visit again. Goods once sold will not be taken back.",
    val warTerms: String = "Warranty covers manufacturing defects only. Physical or water damage, broken or tampered products are not covered. Original bill must be shown for any claim.",
    val warTermsHi: String = "वारंटी केवल निर्माण संबंधी दोषों (मैन्युफैक्चरिंग डिफेक्ट) पर लागू है। शारीरिक या पानी से हुई क्षति, टूटे हुए या छेड़छाड़ किए गए उत्पाद वारंटी में शामिल नहीं हैं। किसी भी दावे के लिए मूल बिल दिखाना अनिवार्य है।",
    val warHiFor: String = "Warranty covers manufacturing defects only. Physical or water damage, broken or tampered products are not covered. Original bill must be shown for any claim.",
    val warLang: String = "en"
)

@Immutable
@Serializable
data class AppSettings(
    val gstMode: String = "exc",   // "exc" | "inc"
    val taxType: String = "intra", // "intra" | "inter"
    val dgst: Int = 0,             // default GST bp
    val allowNeg: Boolean = false,
    val lockBefore: Long = 0L
)

@Immutable
@Serializable
data class Bill(
    val id: String,
    val no: String,
    val date: Long,
    val cust: CustomerInfo = CustomerInfo(),
    val lines: List<BillLine>,
    val disc: BillDiscount = BillDiscount(),
    val gstBp: Int = 0,
    val gstMode: String = "exc",
    val taxType: String = "intra",
    val pay: String = "Cash",
    val tot: BillTotals,
    val shop: ShopInfo = ShopInfo(),
    val returns: List<BillReturn> = emptyList(),
    val sig: SigSnapshot? = null,
    val dsig: DigitalSig? = null,
    val status: String = "active", // active | partial | returned | cancelled
    val ver: Int = 1,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val devId: String = "",
    val sync: SyncMeta = SyncMeta()
)

@Immutable
@Serializable
data class WSaleLine(
    val itemId: String,
    val name: String,
    val sn: String = "",
    val model: String = "",
    val qty: Int,
    val op: Long,     // list price per unit paise
    val gross: Long,  // op * qty
    val disc: Long,   // gross - total
    val total: Long,  // final selling price for line
    val ws: String = "N",
    val war: String = "",
    val swar: String = ""
)

@Immutable
@Serializable
data class WSaleTotals(
    val qty: Int,
    val orig: Long,
    val disc: Long,
    val total: Long
)

@Immutable
@Serializable
data class WSaleVoid(
    val ts: Long,
    val reason: String = "",
    val id: String = ""
)

@Immutable
@Serializable
data class WSale(
    val id: String,
    val no: String,
    val type: String = "without_bill",
    val date: Long,
    val lines: List<WSaleLine>,
    val tot: WSaleTotals,
    val pay: String = "Cash",
    val cust: CustomerInfo = CustomerInfo(),
    val note: String = "",
    val dev: String = "",
    val did: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val ver: Int = 1,
    val sync: SyncMeta = SyncMeta(),
    val voidInfo: WSaleVoid? = null
)

@Immutable
@Serializable
data class StockMove(
    val id: String,
    val itemId: String,
    val type: String, // opening | purchase | sale | return | supplier_return | damaged | adjust
    val qty: Int,
    val ts: Long,
    val ref: String? = null,
    val refNo: String = "",
    val note: String = "",
    val cost: Long = 0L,
    val devId: String = ""
)

@Immutable
@Serializable
data class Category(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Immutable
@Serializable
data class AuditEntry(
    val id: String,
    val ts: Long,
    val type: String,
    val ref: String = "",
    val detail: String = ""
)

@Immutable
@Serializable
data class ServerKeyEntry(
    val alg: String,
    val spki: String,
    val status: String = "active"
)

@Immutable
@Serializable
data class TrustedKeyEntry(
    val alg: String,
    val spki: String,
    val status: String = "active",
    val revokedAt: Long = 0L,
    val issuer: String = ""
)

@Immutable
@Serializable
data class TrustBundle(
    val seq: Long,
    val keys: Map<String, TrustedKeyEntry> = emptyMap(),
    val serverKeys: Map<String, ServerKeyEntry> = emptyMap(),
    val issuedAt: Long = 0L
)

@Immutable
@Serializable
data class PairingQrPayload(
    val v: Int = 1,
    val u: String, // server URL
    val t: String, // one-time token
    val i: String, // issuer
    val e: Long,   // expiresAt
    val r: TrustRootInfo
)

@Immutable
@Serializable
data class TrustRootInfo(
    val skid: String,
    val alg: String,
    val spki: String
)

@Immutable
@Serializable
data class ServerEnvelope(
    val p: JsonObject,
    val skid: String,
    val alg: String? = null,
    val sig: String
)

@Immutable
@Serializable
data class LinkedDevice(
    val kid: String,
    val deviceId: String,
    val name: String,
    val platform: String,
    val status: String,
    val connection: String,
    val pairedAt: Long,
    val lastActive: Long,
    val lastSync: Long,
    val revokedAt: Long = 0L
)

enum class UserRole {
    OWNER, STAFF
}
