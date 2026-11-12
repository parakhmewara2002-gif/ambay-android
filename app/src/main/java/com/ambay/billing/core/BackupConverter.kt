package com.ambay.billing.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/**
 * Lossless converter between Room database domain objects and the website's
 * exact JSON backup format (schema 2 snapshot + v1 legacy migration).
 */
object BackupConverter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class SnapshotBundle(
        val schema: Int = 2,
        val type: String = "snapshot",
        val createdAt: Long = System.currentTimeMillis(),
        val devId: String,
        val counter: Int = 1,
        val wsCounter: Int = 1,
        val shop: ShopInfo = ShopInfo(),
        val settings: AppSettings = AppSettings(),
        val items: List<Item> = emptyList(),
        val bills: List<Bill> = emptyList(),
        val moves: List<StockMove> = emptyList(),
        val cats: List<Category> = emptyList(),
        val wsales: List<WSale> = emptyList()
    )

    data class ParsedBackup(
        val kind: String, // "snapshot" | "bill" | "item" | "wsale" | "legacy"
        val items: List<Item> = emptyList(),
        val bills: List<Bill> = emptyList(),
        val moves: List<StockMove> = emptyList(),
        val cats: List<Category> = emptyList(),
        val wsales: List<WSale> = emptyList(),
        val shop: ShopInfo? = null,
        val settings: AppSettings? = null,
        val counter: Int = 1,
        val wsCounter: Int = 1,
        val rejectedCount: Int = 0
    )

    fun exportSnapshotJson(
        devId: String,
        counter: Int,
        wsCounter: Int,
        shop: ShopInfo,
        settings: AppSettings,
        items: List<Item>,
        bills: List<Bill>,
        moves: List<StockMove>,
        cats: List<Category>,
        wsales: List<WSale>,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val bundle = SnapshotBundle(
            schema = 2,
            type = "snapshot",
            createdAt = nowMs,
            devId = devId,
            counter = counter,
            wsCounter = wsCounter,
            shop = shop,
            settings = settings,
            items = items,
            bills = bills,
            moves = moves,
            cats = cats,
            wsales = wsales
        )
        return json.encodeToString(bundle)
    }

    fun parseBackupJson(rawJson: String): Result<ParsedBackup> = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val schema = root["schema"]?.jsonPrimitive?.intOrNull
        if (schema == null && (root.containsKey("inv") || root.containsKey("hist"))) {
            return@runCatching migrateV1(root)
        }
        require(schema == 2) { "Unsupported or missing schema version: $schema" }
        when (val type = root["type"]?.jsonPrimitive?.contentOrNull) {
            "snapshot" -> parseSnapshot(root)
            "bill" -> {
                val b = parseBill(root["bill"]!!.jsonObject) ?: error("Invalid bill")
                val m = (root["moves"] as? JsonArray)?.mapNotNull { parseMove(it.jsonObject) }.orEmpty()
                ParsedBackup(kind = "bill", bills = listOf(b), moves = m)
            }
            "item" -> {
                val it = parseItem(root["item"]!!.jsonObject) ?: error("Invalid item")
                val m = (root["moves"] as? JsonArray)?.mapNotNull { parseMove(it.jsonObject) }.orEmpty()
                ParsedBackup(kind = "item", items = listOf(it), moves = m)
            }
            "wsale" -> {
                val w = parseWSale(root["sale"]!!.jsonObject) ?: error("Invalid wsale")
                val m = (root["moves"] as? JsonArray)?.mapNotNull { parseMove(it.jsonObject) }.orEmpty()
                ParsedBackup(kind = "wsale", wsales = listOf(w), moves = m)
            }
            else -> error("Unknown backup type: $type")
        }
    }

    private fun parseSnapshot(root: JsonObject): ParsedBackup {
        var rejected = 0
        val items = mutableListOf<Item>()
        (root["items"] as? JsonArray)?.forEach { el ->
            val it = runCatching { parseItem(el.jsonObject) }.getOrNull()
            if (it != null) items += it else rejected++
        }
        val bills = mutableListOf<Bill>()
        (root["bills"] as? JsonArray)?.forEach { el ->
            val b = runCatching { parseBill(el.jsonObject) }.getOrNull()
            if (b != null) bills += b else rejected++
        }
        val moves = mutableListOf<StockMove>()
        (root["moves"] as? JsonArray)?.forEach { el ->
            val m = runCatching { parseMove(el.jsonObject) }.getOrNull()
            if (m != null) moves += m else rejected++
        }
        val cats = (root["cats"] as? JsonArray)?.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                Category(
                    id = o.str("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.str("name").trim(),
                    createdAt = o.lng("createdAt"),
                    updatedAt = o.lng("updatedAt")
                )
            }.getOrNull()
        }.orEmpty()
        val wsales = mutableListOf<WSale>()
        (root["wsales"] as? JsonArray)?.forEach { el ->
            val w = runCatching { parseWSale(el.jsonObject) }.getOrNull()
            if (w != null) wsales += w else rejected++
        }
        val shop = (root["shop"] as? JsonObject)?.let { parseShop(it) } ?: ShopInfo()
        val settings = (root["settings"] as? JsonObject)?.let { parseSettings(it) } ?: AppSettings()
        return ParsedBackup(
            kind = "snapshot",
            items = items,
            bills = bills,
            moves = moves,
            cats = cats,
            wsales = wsales,
            shop = shop,
            settings = settings,
            counter = root.int("counter", 1),
            wsCounter = root.int("wsCounter", 1),
            rejectedCount = rejected
        )
    }

    private fun migrateV1(root: JsonObject): ParsedBackup {
        val now = System.currentTimeMillis()
        val shop = (root["shop"] as? JsonObject)?.let { parseShop(it) } ?: ShopInfo()
        val items = mutableListOf<Item>()
        val moves = mutableListOf<StockMove>()
        (root["inv"] as? JsonArray)?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val name = o.str("name").trim()
            if (name.isEmpty()) return@forEach
            val id = UUID.randomUUID().toString()
            val qInt = o["qty"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val tracked = qInt != null
            items += Item(
                id = id,
                name = name,
                code = o.str("code"),
                fac = o.str("fac"),
                price = MoneyEngine.toPaise(o.str("price").ifBlank { "0" }),
                war = o.str("war"),
                ws = if (o.str("war").isNotBlank()) "W" else "N",
                tracked = tracked,
                qty = if (tracked) qInt else null,
                createdAt = now,
                updatedAt = now
            )
            if (tracked && qInt != 0) {
                moves += StockMove(
                    id = "opening:$id",
                    itemId = id,
                    type = "opening",
                    qty = qInt!!,
                    ts = now,
                    note = "migrated from v1 stock"
                )
            }
        }
        return ParsedBackup(
            kind = "legacy",
            items = items,
            moves = moves,
            shop = shop,
            counter = root.int("counter", 1)
        )
    }

    private fun parseItem(o: JsonObject): Item? {
        val name = o.str("name").trim()
        if (name.isEmpty()) return null
        val tracked = o.bool("tracked")
        val war = o.str("war")
        val swar = o.str("swar")
        val ws = when (o.str("ws")) {
            "W" -> "W"
            "N" -> "N"
            else -> if (war.isNotEmpty() || swar.isNotEmpty()) "W" else "N"
        }
        return Item(
            id = o.str("id").ifBlank { UUID.randomUUID().toString() },
            name = name,
            sn = o.str("sn"),
            model = o.str("model"),
            code = o.str("code"),
            fac = o.str("fac"),
            cat = o.str("cat").ifBlank { "Uncategorized" },
            desc = o.str("desc"),
            price = o.lng("price"),
            cost = o.lng("cost"),
            gst = o["gst"]?.jsonPrimitive?.intOrNull,
            ws = ws,
            war = if (ws == "N") "" else war,
            swar = if (ws == "N") "" else swar,
            md = o["md"]?.jsonPrimitive?.longOrNull,
            rl = o["rl"]?.jsonPrimitive?.intOrNull,
            rq = o["rq"]?.jsonPrimitive?.intOrNull,
            ser = o.bool("ser"),
            tracked = tracked,
            qty = if (tracked) o.int("qty", 0) else null,
            deleted = o.bool("deleted"),
            deletedAt = o.lng("deletedAt"),
            restoredAt = o.lng("restoredAt"),
            createdAt = o.lng("createdAt"),
            updatedAt = o.lng("updatedAt"),
            fv = o.int("fv", 1),
            rev = o.int("rev", 1),
            devId = o.str("devId")
        )
    }

    private fun parseMove(o: JsonObject): StockMove? {
        val id = o.str("id")
        val itemId = o.str("itemId")
        val type = o.str("type")
        val qty = o.int("qty", 0)
        if (id.isBlank() || itemId.isBlank() || qty == 0) return null
        return StockMove(
            id = id,
            itemId = itemId,
            type = type,
            qty = qty,
            ts = o.lng("ts"),
            ref = o["ref"]?.jsonPrimitive?.contentOrNull,
            refNo = o.str("refNo"),
            note = o.str("note"),
            cost = o.lng("cost"),
            devId = o.str("devId")
        )
    }

    private fun parseBill(o: JsonObject): Bill? {
        val id = o.str("id")
        val no = o.str("no")
        if (id.isBlank() || no.isBlank()) return null
        val linesArr = o["lines"] as? JsonArray ?: return null
        val lines = linesArr.mapNotNull { el ->
            val lo = el as? JsonObject ?: return@mapNotNull null
            BillLine(
                itemId = lo["itemId"]?.jsonPrimitive?.contentOrNull,
                name = lo.str("name"),
                sn = lo.str("sn"),
                model = lo.str("model"),
                qty = lo.int("qty", 1),
                rate = lo.lng("rate"),
                cost = lo.lng("cost"),
                op = lo["op"]?.jsonPrimitive?.longOrNull,
                ws = lo.str("ws").ifBlank { "N" },
                war = lo.str("war"),
                swar = lo.str("swar"),
                gstBp = lo.int("gstBp", 0),
                gross = lo.lng("gross"),
                disc = lo.lng("disc"),
                net = lo.lng("net"),
                taxable = lo.lng("taxable"),
                tax = lo.lng("tax"),
                total = lo.lng("total")
            )
        }
        if (lines.isEmpty()) return null
        val to = o["tot"]?.jsonObject ?: return null
        val tot = BillTotals(
            sub = to.lng("sub"),
            disc = to.lng("disc"),
            taxable = to.lng("taxable"),
            tax = to.lng("tax"),
            cgst = to.lng("cgst"),
            sgst = to.lng("sgst"),
            igst = to.lng("igst"),
            total = to.lng("total"),
            cost = to.lng("cost"),
            profit = to.lng("profit")
        )
        val discObj = o["disc"] as? JsonObject
        val disc = BillDiscount(
            type = discObj?.str("type")?.ifBlank { "amt" } ?: "amt",
            valPaiseOrBp = discObj?.lng("val") ?: 0L
        )
        val custObj = o["cust"] as? JsonObject
        val dsigObj = o["dsig"] as? JsonObject
        val dsig = dsigObj?.let {
            DigitalSig(
                v = it.int("v", 1),
                alg = it.str("alg"),
                kid = it.str("kid"),
                dg = it.str("dg"),
                sig = it.str("sig"),
                ts = it.lng("ts"),
                tz = it.str("tz")
            )
        }
        return Bill(
            id = id,
            no = no,
            date = o.lng("date"),
            cust = CustomerInfo(custObj?.str("name").orEmpty(), custObj?.str("phone").orEmpty()),
            lines = lines,
            disc = disc,
            gstBp = o.int("gstBp", 0),
            gstMode = o.str("gstMode").ifBlank { "exc" },
            taxType = o.str("taxType").ifBlank { "intra" },
            pay = o.str("pay").ifBlank { "Cash" },
            tot = tot,
            shop = (o["shop"] as? JsonObject)?.let { parseShop(it) } ?: ShopInfo(),
            dsig = dsig,
            status = o.str("status").ifBlank { "active" },
            ver = o.int("ver", 1),
            createdAt = o.lng("createdAt"),
            updatedAt = o.lng("updatedAt"),
            devId = o.str("devId")
        )
    }

    private fun parseWSale(o: JsonObject): WSale? {
        val id = o.str("id")
        val no = o.str("no")
        if (id.isBlank() || no.isBlank()) return null
        val linesArr = o["lines"] as? JsonArray ?: return null
        val lines = linesArr.mapNotNull { el ->
            val lo = el as? JsonObject ?: return@mapNotNull null
            WSaleLine(
                itemId = lo.str("itemId"),
                name = lo.str("name"),
                sn = lo.str("sn"),
                model = lo.str("model"),
                qty = lo.int("qty", 1),
                op = lo.lng("op"),
                gross = lo.lng("gross"),
                disc = lo.lng("disc"),
                total = lo.lng("total"),
                ws = lo.str("ws").ifBlank { "N" },
                war = lo.str("war"),
                swar = lo.str("swar")
            )
        }
        val to = o["tot"]?.jsonObject ?: return null
        val custObj = o["cust"] as? JsonObject
        val voidObj = o["void"] as? JsonObject
        return WSale(
            id = id,
            no = no,
            date = o.lng("date"),
            lines = lines,
            tot = WSaleTotals(
                qty = to.int("qty", 0),
                orig = to.lng("orig"),
                disc = to.lng("disc"),
                total = to.lng("total")
            ),
            pay = o.str("pay").ifBlank { "Cash" },
            cust = CustomerInfo(custObj?.str("name").orEmpty(), custObj?.str("phone").orEmpty()),
            note = o.str("note"),
            dev = o.str("dev"),
            did = o.str("did"),
            createdAt = o.lng("createdAt"),
            updatedAt = o.lng("updatedAt"),
            ver = o.int("ver", 1),
            voidInfo = voidObj?.let { WSaleVoid(it.lng("ts"), it.str("reason"), it.str("id")) }
        )
    }

    private fun parseShop(o: JsonObject): ShopInfo {
        val def = ShopInfo()
        return ShopInfo(
            name = o.str("name").ifBlank { def.name },
            ph = o.str("ph").ifBlank { def.ph },
            addr = o.str("addr"),
            gst = o.str("gst"),
            foot = o.str("foot").ifBlank { def.foot },
            warTerms = o.str("warTerms").ifBlank { def.warTerms },
            warTermsHi = o.str("warTermsHi").ifBlank { def.warTermsHi },
            warHiFor = o.str("warHiFor").ifBlank { def.warHiFor },
            warLang = o.str("warLang").ifBlank { "en" }
        )
    }

    private fun parseSettings(o: JsonObject): AppSettings = AppSettings(
        gstMode = if (o.str("gstMode") == "inc") "inc" else "exc",
        taxType = if (o.str("taxType") == "inter") "inter" else "intra",
        dgst = o.int("dgst", 0),
        allowNeg = o.bool("allowNeg"),
        lockBefore = o.lng("lockBefore")
    )

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String, def: Int = 0): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: def

    private fun JsonObject.lng(key: String, def: Long = 0L): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: def

    private fun JsonObject.bool(key: String, def: Boolean = false): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: def
}
