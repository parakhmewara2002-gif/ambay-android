package com.ambay.billing.data.repository

import androidx.room.withTransaction
import com.ambay.billing.core.AppSettings
import com.ambay.billing.core.BackupConverter
import com.ambay.billing.core.Bill
import com.ambay.billing.core.BillDiscount
import com.ambay.billing.core.BillLine
import com.ambay.billing.core.BillReturn
import com.ambay.billing.core.BillReturnLine
import com.ambay.billing.core.BillTotals
import com.ambay.billing.core.CalcLineInput
import com.ambay.billing.core.CalcOptions
import com.ambay.billing.core.Category
import com.ambay.billing.core.CustomerInfo
import com.ambay.billing.core.DigitalSig
import com.ambay.billing.core.Item
import com.ambay.billing.core.MoneyEngine
import com.ambay.billing.core.ShopInfo
import com.ambay.billing.core.SigSnapshot
import com.ambay.billing.core.StockMove
import com.ambay.billing.core.SyncMeta
import com.ambay.billing.core.TrustCrypto
import com.ambay.billing.core.WSale
import com.ambay.billing.core.WSaleLine
import com.ambay.billing.core.WSaleTotals
import com.ambay.billing.core.WSaleVoid
import com.ambay.billing.data.local.AmbayDatabase
import com.ambay.billing.data.local.AuditEntity
import com.ambay.billing.data.local.BillEntity
import com.ambay.billing.data.local.CategoryEntity
import com.ambay.billing.data.local.ItemEntity
import com.ambay.billing.data.local.PendingQueueEntity
import com.ambay.billing.data.local.SecurePrefs
import com.ambay.billing.data.local.SettingEntity
import com.ambay.billing.data.local.StockMoveEntity
import com.ambay.billing.data.local.WSaleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SaleCartLineInput(
    val itemId: String,
    val qty: Int,
    val unitRatePaise: Long,
    val war: String = "",
    val swar: String = ""
)

@Singleton
class BillingRepository @Inject constructor(
    private val db: AmbayDatabase,
    private val securePrefs: SecurePrefs
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val KEY_SHOP = "shop_info"
        private const val KEY_SETTINGS = "app_settings"
        private const val KEY_BILL_COUNTER = "bill_counter"
        private const val KEY_WS_COUNTER = "ws_counter"
    }

    fun observeActiveItems(): Flow<List<Item>> =
        db.itemDao().observeActive().map { list -> list.map { it.toDomain() } }

    fun observeAllItems(): Flow<List<Item>> =
        db.itemDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeBills(): Flow<List<Bill>> =
        db.billDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeWSales(): Flow<List<WSale>> =
        db.wSaleDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observePendingQueueCount(): Flow<Int> =
        db.pendingQueueDao().observeAll().map { list -> list.count { it.state == "pending" } }

    fun observeCategories(): Flow<List<Category>> =
        db.categoryDao().observeAll().map { list -> list.map { Category(it.id, it.name, it.createdAt, it.updatedAt) } }

    suspend fun searchItems(query: String): List<Item> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return db.itemDao().getActive().map { it.toDomain() }
        val tokens = trimmed.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "${it.replace("\"", "")}*" }
        return if (tokens.isBlank()) {
            db.itemDao().getActive().map { it.toDomain() }
        } else {
            runCatching { db.itemDao().searchFts(tokens).map { it.toDomain() } }
                .getOrElse {
                    val q = MoneyEngine.normName(trimmed)
                    db.itemDao().getActive().map { it.toDomain() }.filter {
                        MoneyEngine.normName("${it.name} ${it.model} ${it.code} ${it.fac} ${it.cat}").contains(q)
                    }
                }
        }
    }

    suspend fun findExactByBarcode(scanned: String): Item? {
        val active = db.itemDao().getActive().map { it.toDomain() }
        return MoneyEngine.matchCode(active, scanned).firstOrNull()
    }

    suspend fun getShopInfo(): ShopInfo {
        val raw = db.settingDao().get(KEY_SHOP) ?: return ShopInfo()
        return runCatching { json.decodeFromString<ShopInfo>(raw) }.getOrDefault(ShopInfo())
    }

    suspend fun saveShopInfo(shop: ShopInfo) {
        db.settingDao().put(SettingEntity(KEY_SHOP, json.encodeToString(shop)))
    }

    suspend fun getAppSettings(): AppSettings {
        val raw = db.settingDao().get(KEY_SETTINGS) ?: return AppSettings()
        return runCatching { json.decodeFromString<AppSettings>(raw) }.getOrDefault(AppSettings())
    }

    suspend fun saveAppSettings(settings: AppSettings) {
        db.settingDao().put(SettingEntity(KEY_SETTINGS, json.encodeToString(settings)))
    }

    /**
     * Atomic Without-Bill sale: sale + lines + stock movements + pending queue entry in ONE Room transaction.
     * Idempotent on `saleId`. Uses device short-id in WB number to avoid offline collisions across devices.
     */
    suspend fun saveWSale(
        saleId: String = UUID.randomUUID().toString(),
        linesInput: List<SaleCartLineInput>,
        discType: String = "amt",
        discVal: Long = 0L,
        pay: String = "Cash",
        cust: CustomerInfo = CustomerInfo(),
        note: String = "",
        nowMs: Long = System.currentTimeMillis()
    ): WSale = db.withTransaction {
        val existing = db.wSaleDao().getById(saleId)
        if (existing != null) return@withTransaction existing.toDomain()
        require(linesInput.isNotEmpty() && linesInput.size <= 200) { "Cart khali hai" }

        val settings = getAppSettings()
        if (settings.lockBefore > 0L && nowMs < settings.lockBefore) {
            error("Date locked hai")
        }

        val allItems = db.itemDao().getActive().associate { it.id to it.toDomain() }
        val calcInputs = mutableListOf<CalcLineInput>()
        val resolvedItems = mutableListOf<Item>()

        for (inp in linesInput) {
            val it = allItems[inp.itemId] ?: error("Item nahi mila: ${inp.itemId}")
            require(!it.ser) { "${it.name}: Serial/IMEI wale item ka Paper Bill banao" }
            require(inp.qty in 1..MoneyEngine.QTY_MAX) { "Quantity galat hai" }
            require(inp.unitRatePaise in 0L..MoneyEngine.PAISE_MAX) { "Price galat hai" }
            calcInputs += CalcLineInput(inp.qty, inp.unitRatePaise, it.cost, 0)
            resolvedItems += it
        }

        val calc = MoneyEngine.calcBill(
            calcInputs,
            CalcOptions(discType = discType, discVal = discVal, gstBp = 0, mode = "exc", taxType = "intra")
        )

        val discErrs = MoneyEngine.discProblems(
            allItems,
            linesInput.map { it.itemId to it.qty },
            calc.lines.map { it.net }
        )
        if (discErrs.isNotEmpty()) {
            val first = discErrs.first()
            error("${first.name}: max discount ${MoneyEngine.fmtINR(first.allowed)} (min ${MoneyEngine.fmtINR(first.minTotal)})")
        }

        // Check stock
        val needed = mutableMapOf<String, Int>()
        linesInput.forEach { needed[it.itemId] = (needed[it.itemId] ?: 0) + it.qty }
        for ((iid, needQty) in needed) {
            val it = allItems[iid] ?: continue
            if (it.tracked && !settings.allowNeg) {
                val avail = db.stockMoveDao().stockOf(iid)
                require(avail >= needQty) { "${it.name}: sirf $avail stock me hai" }
            }
        }

        val counter = (db.settingDao().get(KEY_WS_COUNTER)?.toIntOrNull() ?: 1)
        val devShort = securePrefs.getDeviceId().takeLast(4).uppercase()
        val saleNo = "WB-$devShort-${counter.toString().padStart(4, '0')}"
        db.settingDao().put(SettingEntity(KEY_WS_COUNTER, (counter + 1).toString()))

        val wLines = calc.lines.mapIndexed { idx, cl ->
            val it = resolvedItems[idx]
            val inp = linesInput[idx]
            val gross = it.price * cl.qty
            val war = if (it.ws == "N") "" else inp.war.ifBlank { it.war }
            val swar = if (it.ws == "N") "" else inp.swar.ifBlank { it.swar }
            WSaleLine(
                itemId = it.id,
                name = it.name,
                sn = it.sn,
                model = it.model,
                qty = cl.qty,
                op = it.price,
                gross = gross,
                disc = (gross - cl.net).coerceAtLeast(0L),
                total = cl.net,
                ws = if (war.isNotEmpty() || swar.isNotEmpty() || it.ws == "W") "W" else "N",
                war = war,
                swar = swar
            )
        }

        val tot = WSaleTotals(
            qty = wLines.sumOf { it.qty },
            orig = wLines.sumOf { it.gross },
            disc = wLines.sumOf { it.disc },
            total = wLines.sumOf { it.total }
        )

        val sale = WSale(
            id = saleId,
            no = saleNo,
            date = nowMs,
            lines = wLines,
            tot = tot,
            pay = pay,
            cust = cust,
            note = note,
            dev = securePrefs.getDeviceId(),
            did = securePrefs.getDeviceId(),
            createdAt = nowMs,
            updatedAt = nowMs,
            ver = 1,
            sync = SyncMeta(state = "pending")
        )

        db.wSaleDao().upsert(sale.toEntity(json))

        // Atomic stock movements
        wLines.forEachIndexed { idx, l ->
            val it = allItems[l.itemId]
            if (it != null && it.tracked) {
                val move = StockMoveEntity(
                    id = "wsale:$saleId:$idx",
                    itemId = it.id,
                    type = "sale",
                    qty = -l.qty,
                    ts = nowMs,
                    ref = saleId,
                    refNo = saleNo,
                    note = "Without Bill $saleNo",
                    cost = it.cost,
                    devId = securePrefs.getDeviceId()
                )
                db.stockMoveDao().insertIgnore(move)
                val newQty = db.stockMoveDao().stockOf(it.id)
                db.itemDao().updateQty(it.id, newQty)
            }
        }

        db.pendingQueueDao().upsert(
            PendingQueueEntity(
                id = UUID.randomUUID().toString(),
                kind = "sale",
                rid = saleId,
                ver = 1,
                state = "pending",
                attempts = 0,
                nextAt = 0L,
                lastErr = null,
                createdAt = nowMs,
                metaName = saleNo
            )
        )

        db.auditDao().insert(
            AuditEntity(
                id = UUID.randomUUID().toString(),
                ts = nowMs,
                type = "wsale_created",
                ref = saleId,
                detail = "$saleNo total ${tot.total} qty ${tot.qty}"
            )
        )

        sale
    }

    /**
     * Atomic Paper Bill (GST) creation with anti-fake digital signature (`bill.dsig`).
     */
    suspend fun saveBill(
        billId: String = UUID.randomUUID().toString(),
        linesInput: List<BillLine>,
        discType: String = "amt",
        discVal: Long = 0L,
        gstBp: Int = 0,
        gstMode: String = "exc",
        taxType: String = "intra",
        pay: String = "Cash",
        cust: CustomerInfo = CustomerInfo(),
        warLang: String = "en",
        nowMs: Long = System.currentTimeMillis()
    ): Bill = db.withTransaction {
        val existing = db.billDao().getById(billId)
        if (existing != null) return@withTransaction existing.toDomain()
        require(linesInput.isNotEmpty()) { "Add at least one item" }

        val settings = getAppSettings()
        if (settings.lockBefore > 0L && nowMs < settings.lockBefore) {
            error("Is date ka bill nahi ban sakta (date locked)")
        }

        val allItems = db.itemDao().getActive().associate { it.id to it.toDomain() }
        val calcInputs = linesInput.map { l ->
            CalcLineInput(l.qty, l.rate, l.cost, l.gstBp)
        }
        val calc = MoneyEngine.calcBill(
            calcInputs,
            CalcOptions(discType, discVal, gstBp, gstMode, taxType)
        )

        val discErrs = MoneyEngine.discProblems(
            allItems,
            linesInput.map { it.itemId to it.qty },
            calc.lines.map { it.net }
        )
        if (discErrs.isNotEmpty()) {
            val first = discErrs.first()
            error("${first.name}: max discount ${MoneyEngine.fmtINR(first.allowed)}")
        }

        // Stock check
        val needed = mutableMapOf<String, Int>()
        linesInput.forEach { l -> l.itemId?.let { needed[it] = (needed[it] ?: 0) + l.qty } }
        for ((iid, needQty) in needed) {
            val it = allItems[iid] ?: continue
            if (it.tracked && !settings.allowNeg) {
                val avail = db.stockMoveDao().stockOf(iid)
                require(avail >= needQty) { "${it.name}: sirf $avail stock me hai" }
            }
        }

        val counter = db.settingDao().get(KEY_BILL_COUNTER)?.toIntOrNull() ?: 1
        val devShort = securePrefs.getDeviceId().takeLast(4).uppercase()
        val billNo = "AMB-$devShort-${counter.toString().padStart(4, '0')}"
        db.settingDao().put(SettingEntity(KEY_BILL_COUNTER, (counter + 1).toString()))

        val finalLines = calc.lines.mapIndexed { idx, cl ->
            val src = linesInput[idx]
            src.copy(
                gstBp = cl.gstBp,
                gross = cl.gross,
                disc = cl.disc,
                net = cl.net,
                taxable = cl.taxable,
                tax = cl.tax,
                total = cl.total
            )
        }

        val shop = getShopInfo().copy(warLang = warLang)
        val devId = securePrefs.getDeviceId()
        val unsignedBill = Bill(
            id = billId,
            no = billNo,
            date = nowMs,
            cust = cust,
            lines = finalLines,
            disc = BillDiscount(discType, discVal),
            gstBp = gstBp,
            gstMode = gstMode,
            taxType = taxType,
            pay = pay,
            tot = BillTotals(
                sub = calc.sub,
                disc = calc.disc,
                taxable = calc.taxable,
                tax = calc.tax,
                cgst = calc.cgst,
                sgst = calc.sgst,
                igst = calc.igst,
                total = calc.total,
                cost = calc.cost,
                profit = calc.profit
            ),
            shop = shop,
            returns = emptyList(),
            sig = SigSnapshot(enabled = false),
            dsig = null,
            status = "active",
            ver = 1,
            createdAt = nowMs,
            updatedAt = nowMs,
            devId = devId,
            sync = SyncMeta(state = "pending")
        )

        // Sign bill with Android Keystore EC P-256 key (write-once)
        val keyInfo = securePrefs.ensureDeviceKey()
        val tz = TimeZone.getDefault().id
        val manifest = TrustCrypto.buildManifestMap(unsignedBill, keyInfo.kid, tz)
        val dg = TrustCrypto.manifestDigest(manifest)
        val sig = securePrefs.signWithDeviceKey(keyInfo.kid, TrustCrypto.sigMessage(dg))
        val signedBill = unsignedBill.copy(
            dsig = DigitalSig(v = 1, alg = keyInfo.alg, kid = keyInfo.kid, dg = dg, sig = sig, ts = nowMs, tz = tz)
        )

        db.billDao().upsert(signedBill.toEntity(json))

        finalLines.forEachIndexed { idx, l ->
            val it = l.itemId?.let { allItems[it] }
            if (it != null && it.tracked) {
                db.stockMoveDao().insertIgnore(
                    StockMoveEntity(
                        id = "sale:$billId:$idx",
                        itemId = it.id,
                        type = "sale",
                        qty = -l.qty,
                        ts = nowMs,
                        ref = billId,
                        refNo = billNo,
                        note = "Sale $billNo",
                        cost = it.cost,
                        devId = devId
                    )
                )
                db.itemDao().updateQty(it.id, db.stockMoveDao().stockOf(it.id))
            }
        }

        db.pendingQueueDao().upsert(
            PendingQueueEntity(
                id = UUID.randomUUID().toString(),
                kind = "bill",
                rid = billId,
                ver = 1,
                state = "pending",
                attempts = 0,
                nextAt = 0L,
                lastErr = null,
                createdAt = nowMs,
                metaName = billNo
            )
        )

        db.auditDao().insert(
            AuditEntity(UUID.randomUUID().toString(), nowMs, "bill_created", billId, "$billNo total ${calc.total}")
        )

        signedBill
    }

    suspend fun voidWSale(saleId: String, reason: String) = db.withTransaction {
        val ent = db.wSaleDao().getById(saleId) ?: error("Sale nahi mila")
        val w = ent.toDomain()
        if (w.voidInfo != null) return@withTransaction
        val now = System.currentTimeMillis()
        val updated = w.copy(
            voidInfo = WSaleVoid(ts = now, reason = reason.take(200), id = UUID.randomUUID().toString()),
            ver = w.ver + 1,
            updatedAt = now,
            sync = w.sync.copy(state = "pending")
        )
        db.wSaleDao().upsert(updated.toEntity(json))
        updated.lines.forEachIndexed { idx, l ->
            val it = db.itemDao().getById(l.itemId)
            if (it != null && it.tracked) {
                db.stockMoveDao().insertIgnore(
                    StockMoveEntity(
                        id = "wsv:${w.id}:$idx",
                        itemId = it.id,
                        type = "return",
                        qty = l.qty,
                        ts = now,
                        ref = updated.voidInfo!!.id,
                        refNo = w.no,
                        note = "Void Without Bill ${w.no}",
                        cost = it.cost,
                        devId = securePrefs.getDeviceId()
                    )
                )
                db.itemDao().updateQty(it.id, db.stockMoveDao().stockOf(it.id))
            }
        }
        db.pendingQueueDao().upsert(
            PendingQueueEntity(UUID.randomUUID().toString(), "sale", saleId, updated.ver, "pending", 0, 0L, null, now, w.no)
        )
    }

    suspend fun cancelBill(billId: String, reason: String) = db.withTransaction {
        val ent = db.billDao().getById(billId) ?: error("Bill nahi mila")
        val b = ent.toDomain()
        if (b.status == "cancelled") return@withTransaction
        val now = System.currentTimeMillis()
        val retId = UUID.randomUUID().toString()
        val retLines = b.lines.mapIndexedNotNull { idx, l ->
            val (doneQty, doneAmt, doneTax) = MoneyEngine.retSumsForLine(b, idx)
            val rem = l.qty - doneQty
            if (rem <= 0) null
            else BillReturnLine(
                idx = idx,
                qty = rem,
                amt = l.total - doneAmt,
                taxable = l.taxable,
                tax = l.tax - doneTax,
                cost = l.cost * rem
            )
        }
        if (retLines.isEmpty()) return@withTransaction
        val ret = BillReturn(
            id = retId,
            date = now,
            lines = retLines,
            amt = retLines.sumOf { it.amt },
            taxable = retLines.sumOf { it.taxable },
            tax = retLines.sumOf { it.tax },
            cost = retLines.sumOf { it.cost },
            reason = reason.take(200),
            cancel = true
        )
        val updated = b.copy(
            returns = b.returns + ret,
            status = "cancelled",
            ver = b.ver + 1,
            updatedAt = now,
            sync = b.sync.copy(state = "pending")
        )
        db.billDao().upsert(updated.toEntity(json))
        retLines.forEach { rl ->
            val l = b.lines[rl.idx]
            val it = l.itemId?.let { db.itemDao().getById(it) }
            if (it != null && it.tracked) {
                db.stockMoveDao().insertIgnore(
                    StockMoveEntity(
                        id = "ret:$retId:${rl.idx}",
                        itemId = it.id,
                        type = "return",
                        qty = rl.qty,
                        ts = now,
                        ref = retId,
                        refNo = b.no,
                        note = "Cancelled ${b.no}",
                        cost = it.cost,
                        devId = securePrefs.getDeviceId()
                    )
                )
                db.itemDao().updateQty(it.id, db.stockMoveDao().stockOf(it.id))
            }
        }
    }

    suspend fun upsertItem(item: Item, openingQty: Int? = null, forceDuplicate: Boolean = false): Item = db.withTransaction {
        val all = db.itemDao().getActive().map { it.toDomain() }
        val isNew = all.none { it.id == item.id }
        if (!forceDuplicate) {
            val dup = all.firstOrNull { it.id != item.id && MoneyEngine.sameVariant(it, item.name, item.model) }
            require(dup == null) { "DUPLICATE_NAME:${dup?.name}" }
        }
        val myCodes = MoneyEngine.itemCodes(item).toSet()
        for (other in all) {
            if (other.id == item.id) continue
            val hit = MoneyEngine.itemCodes(other).firstOrNull { it in myCodes }
            require(hit == null) { "Barcode $hit pehle se '${other.name}' me hai" }
        }

        val now = System.currentTimeMillis()
        val finalCode = item.code.ifBlank { "ITM" + item.id.replace("-", "").take(8).uppercase() }
        val tracked = item.tracked || (openingQty != null && openingQty >= 0)
        val saved = item.copy(
            code = finalCode,
            tracked = tracked,
            qty = if (tracked) (if (isNew) openingQty ?: 0 else db.stockMoveDao().stockOf(item.id)) else null,
            updatedAt = now,
            fv = if (isNew) 1 else item.fv + 1,
            rev = if (isNew) 1 else item.rev + 1,
            devId = securePrefs.getDeviceId()
        )
        db.itemDao().upsert(saved.toEntity())
        if (isNew && openingQty != null && openingQty > 0) {
            db.stockMoveDao().insertIgnore(
                StockMoveEntity(
                    id = "opening:${saved.id}",
                    itemId = saved.id,
                    type = "opening",
                    qty = openingQty,
                    ts = now,
                    ref = null,
                    refNo = "",
                    note = "Opening stock",
                    cost = saved.cost,
                    devId = securePrefs.getDeviceId()
                )
            )
            db.itemDao().updateQty(saved.id, openingQty)
        }
        saved
    }

    suspend fun stockAction(itemId: String, type: String, qtyOrDelta: Int, note: String, costPaise: Long = 0L) = db.withTransaction {
        val it = db.itemDao().getById(itemId) ?: error("Item nahi mila")
        val cur = db.stockMoveDao().stockOf(itemId)
        val now = System.currentTimeMillis()
        val actionId = UUID.randomUUID().toString()
        val signed: Int
        val mtype: String
        var finalNote = note.trim()

        when (type) {
            "purchase", "opening" -> { signed = qtyOrDelta; mtype = type }
            "supplier_return", "damaged" -> { signed = -qtyOrDelta; mtype = type }
            "adjust_by" -> {
                require(finalNote.isNotEmpty()) { "Reason likhna zaroori hai" }
                signed = qtyOrDelta
                mtype = "adjust"
                finalNote = "Adjustment ${if (signed > 0) "+" else ""}$signed: $finalNote"
            }
            "opening_fix" -> {
                require(finalNote.isNotEmpty()) { "Reason likhna zaroori hai" }
                val moves = db.stockMoveDao().getAll().filter { it.itemId == itemId && (it.type == "opening" || it.ref == "opening_fix") }
                require(moves.any { it.type == "opening" }) { "Is item ka opening stock record nahi hai" }
                val orig = moves.sumOf { it.qty }
                signed = qtyOrDelta - orig
                mtype = "adjust"
                finalNote = "Opening stock correction $orig -> $qtyOrDelta: $finalNote"
            }
            else -> error("Galat stock action: $type")
        }
        require(signed != 0) { "Koi badlav nahi hai" }
        val settings = getAppSettings()
        if (cur + signed < 0 && !settings.allowNeg) {
            error("Sirf $cur stock me hai")
        }
        db.stockMoveDao().insertIgnore(
            StockMoveEntity(
                id = "act:$actionId",
                itemId = itemId,
                type = mtype,
                qty = signed,
                ts = now,
                ref = if (type == "opening_fix") "opening_fix" else null,
                refNo = "",
                note = finalNote,
                cost = costPaise,
                devId = securePrefs.getDeviceId()
            )
        )
        val newQty = db.stockMoveDao().stockOf(itemId)
        db.itemDao().upsert(it.copy(tracked = true, qty = newQty, updatedAt = now, rev = it.rev + 1))
    }

    /**
     * Idempotent import of another device's Without-Bill sale (`wsale:<txn>:<lineIndex>`).
     */
    suspend fun importRemoteWSale(w: WSale) = db.withTransaction {
        val ex = db.wSaleDao().getById(w.id)
        if (ex == null) {
            db.wSaleDao().upsert(w.copy(sync = SyncMeta(state = "synced", at = System.currentTimeMillis())).toEntity(json))
            w.lines.forEachIndexed { idx, l ->
                val it = db.itemDao().getById(l.itemId)
                if (it != null && it.tracked) {
                    db.stockMoveDao().insertIgnore(
                        StockMoveEntity(
                            id = "wsale:${w.id}:$idx",
                            itemId = it.id,
                            type = "sale",
                            qty = -l.qty,
                            ts = w.date,
                            ref = w.id,
                            refNo = w.no,
                            note = "Without Bill ${w.no} (synced)",
                            cost = it.cost,
                            devId = w.did
                        )
                    )
                    db.itemDao().updateQty(it.id, db.stockMoveDao().stockOf(it.id))
                }
            }
        }
        if (w.voidInfo != null) {
            w.lines.forEachIndexed { idx, l ->
                val it = db.itemDao().getById(l.itemId)
                if (it != null && it.tracked && db.stockMoveDao().exists("wsale:${w.id}:$idx")) {
                    db.stockMoveDao().insertIgnore(
                        StockMoveEntity(
                            id = "wsv:${w.id}:$idx",
                            itemId = it.id,
                            type = "return",
                            qty = l.qty,
                            ts = w.voidInfo.ts,
                            ref = w.voidInfo.id.ifBlank { w.id },
                            refNo = w.no,
                            note = "Void Without Bill ${w.no} (synced)",
                            cost = it.cost,
                            devId = w.did
                        )
                    )
                    db.itemDao().updateQty(it.id, db.stockMoveDao().stockOf(it.id))
                }
            }
        }
    }

    suspend fun exportBackupJson(): String {
        val items = db.itemDao().getAll().map { it.toDomain() }
        val bills = db.billDao().getAll().map { it.toDomain() }
        val moves = db.stockMoveDao().getAll().map { it.toDomain() }
        val cats = db.categoryDao().getAll().map { Category(it.id, it.name, it.createdAt, it.updatedAt) }
        val wsales = db.wSaleDao().getAll().map { it.toDomain() }
        val counter = db.settingDao().get(KEY_BILL_COUNTER)?.toIntOrNull() ?: 1
        val wsCounter = db.settingDao().get(KEY_WS_COUNTER)?.toIntOrNull() ?: 1
        return BackupConverter.exportSnapshotJson(
            devId = securePrefs.getDeviceId(),
            counter = counter,
            wsCounter = wsCounter,
            shop = getShopInfo(),
            settings = getAppSettings(),
            items = items,
            bills = bills,
            moves = moves,
            cats = cats,
            wsales = wsales
        )
    }

    suspend fun importBackupJson(rawJson: String, replaceAll: Boolean = false): BackupConverter.ParsedBackup {
        val parsed = BackupConverter.parseBackupJson(rawJson).getOrThrow()
        db.withTransaction {
            if (replaceAll && parsed.kind == "snapshot") {
                db.itemDao().clearAll()
                db.billDao().clearAll()
                db.wSaleDao().clearAll()
                db.stockMoveDao().clearAll()
                db.categoryDao().clearAll()
            }
            parsed.shop?.let { saveShopInfo(it) }
            parsed.settings?.let { saveAppSettings(it) }
            if (parsed.items.isNotEmpty()) db.itemDao().upsertAll(parsed.items.map { it.toEntity() })
            if (parsed.bills.isNotEmpty()) db.billDao().upsertAll(parsed.bills.map { it.toEntity(json) })
            if (parsed.wsales.isNotEmpty()) db.wSaleDao().upsertAll(parsed.wsales.map { it.toEntity(json) })
            if (parsed.moves.isNotEmpty()) db.stockMoveDao().insertAllIgnore(parsed.moves.map { it.toEntity() })
            if (parsed.cats.isNotEmpty()) db.categoryDao().upsertAll(parsed.cats.map { CategoryEntity(it.id, it.name, it.createdAt, it.updatedAt) })
            // Recompute cached quantities from movements
            for (it in db.itemDao().getAll()) {
                if (it.tracked) {
                    db.itemDao().updateQty(it.id, db.stockMoveDao().stockOf(it.id))
                }
            }
        }
        return parsed
    }

    // ---- Mappers ----

    private fun ItemEntity.toDomain() = Item(
        id, name, sn, model, code, fac, cat, desc, price, cost, gst, ws, war, swar, md, rl, rq,
        ser, tracked, qty, deleted, deletedAt, restoredAt, createdAt, updatedAt, fv, rev, devId,
        SyncMeta(syncState, syncFileId, syncedVer, syncAt, syncErr)
    )

    private fun Item.toEntity() = ItemEntity(
        id, name, sn, model, code, fac, cat, desc, price, cost, gst, ws, war, swar, md, rl, rq,
        ser, tracked, qty, deleted, deletedAt, restoredAt, createdAt, updatedAt, fv, rev, devId,
        sync.state, sync.fileId, sync.syncedVer, sync.at, sync.err
    )

    private fun BillEntity.toDomain() = Bill(
        id = id,
        no = no,
        date = date,
        cust = CustomerInfo(custName, custPhone),
        lines = json.decodeFromString(linesJson),
        disc = BillDiscount(discType, discVal),
        gstBp = gstBp,
        gstMode = gstMode,
        taxType = taxType,
        pay = pay,
        tot = BillTotals(sub, disc, taxable, tax, cgst, sgst, igst, total, cost, profit),
        shop = json.decodeFromString(shopJson),
        returns = json.decodeFromString(returnsJson),
        sig = sigJson?.let { json.decodeFromString(it) },
        dsig = dsigJson?.let { json.decodeFromString(it) },
        status = status,
        ver = ver,
        createdAt = createdAt,
        updatedAt = updatedAt,
        devId = devId,
        sync = SyncMeta(syncState, syncFileId, syncedVer, syncAt, syncErr)
    )

    private fun Bill.toEntity(j: Json) = BillEntity(
        id = id,
        no = no,
        date = date,
        custName = cust.name,
        custPhone = cust.phone,
        linesJson = j.encodeToString(lines),
        discType = disc.type,
        discVal = disc.valPaiseOrBp,
        gstBp = gstBp,
        gstMode = gstMode,
        taxType = taxType,
        pay = pay,
        sub = tot.sub,
        disc = tot.disc,
        taxable = tot.taxable,
        tax = tot.tax,
        cgst = tot.cgst,
        sgst = tot.sgst,
        igst = tot.igst,
        total = tot.total,
        cost = tot.cost,
        profit = tot.profit,
        shopJson = j.encodeToString(shop),
        returnsJson = j.encodeToString(returns),
        sigJson = sig?.let { j.encodeToString(it) },
        dsigJson = dsig?.let { j.encodeToString(it) },
        status = status,
        ver = ver,
        createdAt = createdAt,
        updatedAt = updatedAt,
        devId = devId,
        syncState = sync.state,
        syncFileId = sync.fileId,
        syncedVer = sync.syncedVer,
        syncAt = sync.at,
        syncErr = sync.err
    )

    private fun WSaleEntity.toDomain() = WSale(
        id = id,
        no = no,
        date = date,
        lines = json.decodeFromString(linesJson),
        tot = WSaleTotals(totQty, totOrig, totDisc, totTotal),
        pay = pay,
        cust = CustomerInfo(custName, custPhone),
        note = note,
        dev = dev,
        did = did,
        createdAt = createdAt,
        updatedAt = updatedAt,
        ver = ver,
        sync = SyncMeta(syncState, syncFileId, syncedVer, syncAt, syncErr),
        voidInfo = voidJson?.let { json.decodeFromString(it) }
    )

    private fun WSale.toEntity(j: Json) = WSaleEntity(
        id = id,
        no = no,
        date = date,
        linesJson = j.encodeToString(lines),
        totQty = tot.qty,
        totOrig = tot.orig,
        totDisc = tot.disc,
        totTotal = tot.total,
        pay = pay,
        custName = cust.name,
        custPhone = cust.phone,
        note = note,
        dev = dev,
        did = did,
        createdAt = createdAt,
        updatedAt = updatedAt,
        ver = ver,
        voidJson = voidInfo?.let { j.encodeToString(it) },
        syncState = sync.state,
        syncFileId = sync.fileId,
        syncedVer = sync.syncedVer,
        syncAt = sync.at,
        syncErr = sync.err
    )

    private fun StockMoveEntity.toDomain() = StockMove(id, itemId, type, qty, ts, ref, refNo, note, cost, devId)
    private fun StockMove.toEntity() = StockMoveEntity(id, itemId, type, qty, ts, ref, refNo, note, cost, devId)
}
