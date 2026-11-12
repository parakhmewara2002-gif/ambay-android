package com.ambay.billing.core

import java.math.BigInteger
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

data class CalcLineInput(
    val qty: Int,
    val rate: Long,
    val cost: Long = 0L,
    val gstBp: Int? = null
)

data class CalcOptions(
    val discType: String = "amt", // "amt" | "pct"
    val discVal: Long = 0L,
    val gstBp: Int = 0,
    val mode: String = "exc",     // "exc" | "inc"
    val taxType: String = "intra" // "intra" | "inter"
)

data class CalcLineOut(
    val qty: Int,
    val rate: Long,
    val cost: Long,
    val gstBp: Int,
    val gross: Long,
    val disc: Long,
    val net: Long,
    val taxable: Long,
    val tax: Long,
    val total: Long
)

data class GstGroupSummary(
    val bp: Int,
    val taxable: Long,
    val tax: Long,
    val cgst: Long,
    val sgst: Long,
    val igst: Long
)

data class BillCalcResult(
    val mode: String,
    val taxType: String,
    val lines: List<CalcLineOut>,
    val sub: Long,
    val disc: Long,
    val taxable: Long,
    val tax: Long,
    val cgst: Long,
    val sgst: Long,
    val igst: Long,
    val total: Long,
    val cost: Long,
    val profit: Long,
    val groups: List<GstGroupSummary>
)

data class DiscountProblem(
    val idx: Int,
    val itemId: String,
    val name: String,
    val qty: Int,
    val price: Long,
    val maxDisc: Long,
    val given: Long,
    val allowed: Long,
    val minSell: Long,
    val minTotal: Long
)

data class ImeiCheckResult(
    val ok: Boolean,
    val empty: Boolean = false,
    val imei: String = "",
    val reason: String = ""
)

data class WarTermsParts(
    val en: String,
    val hi: String,
    val stale: Boolean
)

data class ReorderRow(
    val id: String,
    val name: String,
    val model: String,
    val qty: Int,
    val limit: Int,
    val order: Int?
)

object MoneyEngine {
    const val QTY_MAX = 99_999
    const val PAISE_MAX = 1_000_000_000L // Rs 1 crore per unit
    const val BP_MAX = 10_000

    private val WRX = Regex("^(\\d+)([dmy])$")
    private val WAR_MAX = mapOf("d" to 36500, "m" to 1200, "y" to 100)
    private val INDIA_LOCALE = Locale("en", "IN")

    fun mulDiv(a: Long, b: Long, c: Long): Long {
        if (c == 0L) return 0L
        val ba = BigInteger.valueOf(a)
        val bb = BigInteger.valueOf(b)
        val bc = BigInteger.valueOf(c)
        val two = BigInteger.valueOf(2L)
        return ba.multiply(bb).multiply(two).add(bc).divide(two.multiply(bc)).toLong()
    }

    fun toPaise(raw: String): Long {
        val cleaned = raw.replace(",", "").trim()
        val d = cleaned.toDoubleOrNull() ?: return 0L
        if (!d.isFinite()) return 0L
        val adj = if (d >= 0) 1e-7 else -1e-7
        return kotlin.math.round(d * 100.0 + adj).toLong()
    }

    fun fmtINR(paise: Long): String {
        val neg = paise < 0
        val absP = kotlin.math.abs(paise)
        val rupees = absP / 100
        val frac = absP % 100
        val nf = NumberFormat.getIntegerInstance(INDIA_LOCALE)
        return (if (neg) "-" else "") + "₹" + nf.format(rupees) + "." + frac.toString().padStart(2, '0')
    }

    fun allocate(total: Long, weights: List<Long>): List<Long> {
        val wSum = weights.sum()
        if (wSum == 0L || total == 0L) return List(weights.size) { 0L }
        val bigT = BigInteger.valueOf(total)
        val bigW = BigInteger.valueOf(wSum)
        val base = MutableList(weights.size) { i ->
            bigT.multiply(BigInteger.valueOf(weights[i])).divide(bigW).toLong()
        }
        val rem = (total - base.sum()).toInt()
        val order = weights.indices.map { i ->
            i to bigT.multiply(BigInteger.valueOf(weights[i])).remainder(bigW)
        }.sortedWith(compareByDescending<Pair<Int, BigInteger>> { it.second }.thenBy { it.first })
        for (k in 0 until rem.coerceAtMost(order.size)) {
            base[order[k].first]++
        }
        return base
    }

    fun calcBill(lines: List<CalcLineInput>, opts: CalcOptions): BillCalcResult {
        val mode = if (opts.mode == "inc") "inc" else "exc"
        val taxType = if (opts.taxType == "inter") "inter" else "intra"
        val defBp = opts.gstBp.coerceIn(0, BP_MAX)

        val norm = lines.map { l ->
            val q = l.qty.coerceIn(1, QTY_MAX)
            val r = l.rate.coerceIn(0L, PAISE_MAX)
            val c = l.cost.coerceIn(0L, PAISE_MAX)
            val bp = (l.gstBp ?: defBp).coerceIn(0, BP_MAX)
            Triple(q, r, q.toLong() * r) to (c to bp)
        }

        val sub = norm.sumOf { it.first.third }
        val discTotal = if (opts.discType == "pct") {
            mulDiv(sub, opts.discVal.coerceIn(0L, 10_000L), 10_000L)
        } else {
            opts.discVal.coerceAtLeast(0L).coerceAtMost(sub)
        }
        val al = allocate(discTotal, norm.map { it.first.third })

        val out = norm.mapIndexed { i, (qrt, cbp) ->
            val net = qrt.third - al[i]
            MutableCalcLine(
                qty = qrt.first,
                rate = qrt.second,
                cost = cbp.first,
                gstBp = cbp.second,
                gross = qrt.third,
                disc = al[i],
                net = net
            )
        }

        val byBp = out.indices.groupBy { out[it].gstBp }
        for ((bp, idxs) in byBp) {
            val nets = idxs.map { out[it].net }
            val nSum = nets.sum()
            if (mode == "exc") {
                val tax = mulDiv(nSum, bp.toLong(), 10_000L)
                val ta = allocate(tax, nets)
                idxs.forEachIndexed { j, idx ->
                    out[idx].taxable = out[idx].net
                    out[idx].tax = ta[j]
                    out[idx].total = out[idx].net + ta[j]
                }
            } else {
                val taxableG = mulDiv(nSum, 10_000L, (10_000 + bp).toLong())
                val tb = allocate(taxableG, nets)
                idxs.forEachIndexed { j, idx ->
                    out[idx].taxable = tb[j]
                    out[idx].tax = out[idx].net - tb[j]
                    out[idx].total = out[idx].net
                }
            }
        }

        val groupsMap = linkedMapOf<Int, MutableGstGroup>()
        for (l in out) {
            val g = groupsMap.getOrPut(l.gstBp) { MutableGstGroup(l.gstBp) }
            g.taxable += l.taxable
            g.tax += l.tax
        }

        var cgst = 0L
        var sgst = 0L
        var igst = 0L
        val groups = groupsMap.values.sortedBy { it.bp }.map { g ->
            val gCgst: Long
            val gSgst: Long
            val gIgst: Long
            if (taxType == "inter") {
                gCgst = 0L; gSgst = 0L; gIgst = g.tax
            } else {
                gCgst = (g.tax + 1L) / 2L // Math.ceil(g.tax / 2)
                gSgst = g.tax - gCgst
                gIgst = 0L
            }
            cgst += gCgst; sgst += gSgst; igst += gIgst
            GstGroupSummary(g.bp, g.taxable, g.tax, gCgst, gSgst, gIgst)
        }

        val costTotal = out.sumOf { it.qty.toLong() * it.cost }
        val taxableTotal = out.sumOf { it.taxable }
        val taxTotal = out.sumOf { it.tax }
        val grandTotal = out.sumOf { it.total }

        return BillCalcResult(
            mode = mode,
            taxType = taxType,
            lines = out.map {
                CalcLineOut(it.qty, it.rate, it.cost, it.gstBp, it.gross, it.disc, it.net, it.taxable, it.tax, it.total)
            },
            sub = sub,
            disc = discTotal,
            taxable = taxableTotal,
            tax = taxTotal,
            cgst = cgst,
            sgst = sgst,
            igst = igst,
            total = grandTotal,
            cost = costTotal,
            profit = taxableTotal - costTotal,
            groups = groups
        )
    }

    private data class MutableCalcLine(
        val qty: Int,
        val rate: Long,
        val cost: Long,
        val gstBp: Int,
        val gross: Long,
        val disc: Long,
        val net: Long,
        var taxable: Long = 0L,
        var tax: Long = 0L,
        var total: Long = 0L
    )

    private data class MutableGstGroup(
        val bp: Int,
        var taxable: Long = 0L,
        var tax: Long = 0L
    )

    fun inWords(nInput: Long): String {
        var n = nInput
        if (n <= 0L) return "Zero"
        val a = listOf(
            "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
        )
        val b = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
        fun two(x: Int): String = if (x < 20) a.getOrElse(x - 1) { "" } else b[x / 10] + if (x % 10 != 0) " " + a[x % 10 - 1] else ""
        fun th(x: Int): String = (if (x > 99) a[x / 100 - 1] + " Hundred" + if (x % 100 != 0) " " else "" else "") +
            (if (x % 100 != 0) two(x % 100) else "")
        val sb = StringBuilder()
        val c = n / 10_000_000L; n %= 10_000_000L
        val l = n / 100_000L; n %= 100_000L
        val t = n / 1_000L; n %= 1_000L
        if (c > 0) sb.append(if (c > 99) inWords(c) else two(c.toInt())).append(" Crore ")
        if (l > 0) sb.append(two(l.toInt())).append(" Lakh ")
        if (t > 0) sb.append(two(t.toInt())).append(" Thousand ")
        if (n > 0) sb.append(th(n.toInt()))
        return sb.toString().trim()
    }

    fun amtWords(paise: Long): String {
        val p = paise.coerceAtLeast(0L)
        val r = p / 100
        val rem = p % 100
        return inWords(r) + " Rupees" + (if (rem > 0) " and " + inWords(rem) + " Paise" else "") + " Only"
    }

    fun maxDiscOf(item: Item): Long? = item.md?.let { it.coerceAtLeast(0L).coerceAtMost(item.price) }

    fun minSellOf(item: Item): Long? = maxDiscOf(item)?.let { (item.price - it).coerceAtLeast(0L) }

    fun discProblems(
        items: Map<String, Item>,
        lines: List<Pair<String?, Int>>, // itemId to qty
        nets: List<Long>
    ): List<DiscountProblem> {
        val out = mutableListOf<DiscountProblem>()
        lines.forEachIndexed { k, (itemId, qty) ->
            val it = itemId?.let { items[it] } ?: return@forEachIndexed
            val m = maxDiscOf(it) ?: return@forEachIndexed
            val gross = it.price * qty
            val given = (gross - nets[k]).coerceAtLeast(0L)
            val allowed = m * qty
            if (given > allowed) {
                out += DiscountProblem(
                    idx = k,
                    itemId = it.id,
                    name = it.name,
                    qty = qty,
                    price = it.price,
                    maxDisc = m,
                    given = given,
                    allowed = allowed,
                    minSell = it.price - m,
                    minTotal = gross - allowed
                )
            }
        }
        return out
    }

    fun normName(s: String?): String =
        Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)

    fun scanText(s: String?): String =
        s.orEmpty().trim { it.isWhitespace() || it == '\u0000' || it == '\uFEFF' }

    fun itemCodes(item: Item): List<String> =
        (listOf(item.code) + item.fac.split(Regex("[,;\\s]+")))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun matchCode(items: List<Item>, scanned: String): List<Item> {
        val t = scanText(scanned)
        if (t.isEmpty()) return emptyList()
        return items.filter { !it.deleted && itemCodes(it).contains(t) }
    }

    fun sameVariant(a: Item, bName: String, bModel: String): Boolean =
        normName(a.name) == normName(bName) && normName(a.model) == normName(bModel)

    fun warLabel(v: String): String {
        val m = WRX.matchEntire(v) ?: return ""
        val n = m.groupValues[1].toInt()
        val unit = when (m.groupValues[2]) {
            "d" -> "Day"
            "m" -> "Month"
            else -> "Year"
        }
        return "$n $unit" + if (n > 1) "s" else ""
    }

    fun warLabelHi(v: String): String {
        val m = WRX.matchEntire(v) ?: return ""
        val n = m.groupValues[1].toInt()
        val unit = when (m.groupValues[2]) {
            "d" -> "दिन"
            "m" -> if (n > 1) "महीने" else "महीना"
            else -> "वर्ष"
        }
        return "$n $unit"
    }

    fun warLabelLang(v: String, lang: String): String {
        val e = warLabel(v)
        val h = warLabelHi(v)
        return when (lang) {
            "hi" -> h
            "both" -> if (e.isNotEmpty() && h.isNotEmpty()) "$e / $h" else e
            else -> e
        }
    }

    fun warMake(n: Int, u: String): String {
        val max = WAR_MAX[u] ?: return ""
        if (n < 1 || n > max) return ""
        return "$n$u"
    }

    fun warEnd(v: String, dateMs: Long): Long? {
        val m = WRX.matchEntire(v) ?: return null
        val n = m.groupValues[1].toInt()
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        when (m.groupValues[2]) {
            "d" -> cal.add(Calendar.DAY_OF_MONTH, n)
            "m" -> cal.add(Calendar.MONTH, n)
            "y" -> cal.add(Calendar.YEAR, n)
        }
        return cal.timeInMillis
    }

    fun warTermsParts(shop: ShopInfo, lang: String): WarTermsParts {
        val en = shop.warTerms
        val hi = shop.warTermsHi
        val stale = en.isNotEmpty() && (hi.isBlank() || shop.warHiFor != en)
        val hiOk = hi.isNotBlank() && !stale
        return when (lang) {
            "hi" -> if (hiOk) WarTermsParts("", hi, stale) else WarTermsParts(en, "", stale)
            "both" -> if (hiOk) WarTermsParts(en, hi, stale) else WarTermsParts(en, "", stale)
            else -> WarTermsParts(en, "", stale)
        }
    }

    fun imeiNorm(v: String?): String = v.orEmpty().replace(Regex("[\\s-]"), "")

    fun luhnOk(s: String): Boolean {
        var sum = 0
        for (i in s.indices) {
            var d = s[s.length - 1 - i] - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    fun imeiCheck(v: String?): ImeiCheckResult {
        val s = imeiNorm(v)
        if (s.isEmpty()) return ImeiCheckResult(ok = false, empty = true, reason = "IMEI khaali hai")
        if (!s.all { it.isDigit() }) return ImeiCheckResult(ok = false, imei = s, reason = "IMEI mein sirf ank (0-9) hone chahiye")
        if (s.length != 15) return ImeiCheckResult(ok = false, imei = s, reason = "IMEI 15 ank ka hona chahiye (abhi ${s.length} hai)")
        if (!luhnOk(s)) return ImeiCheckResult(ok = false, imei = s, reason = "IMEI galat hai (check-digit match nahi hua)")
        return ImeiCheckResult(ok = true, imei = s)
    }

    fun isLowItem(item: Item, globalTh: Int): Boolean {
        if (!item.tracked || item.deleted) return false
        val limit = item.rl ?: globalTh
        return (item.qty ?: 0) <= limit
    }

    fun reorderList(items: List<Item>, globalTh: Int): List<ReorderRow> =
        items.filter { isLowItem(it, globalTh) }
            .sortedWith(compareBy<Item> { it.qty ?: 0 }.thenBy { it.name })
            .map {
                ReorderRow(
                    id = it.id,
                    name = it.name,
                    model = it.model,
                    qty = it.qty ?: 0,
                    limit = it.rl ?: globalTh,
                    order = it.rq?.takeIf { q -> q > 0 }
                )
            }

    fun billStatus(b: Bill): String {
        val total = b.lines.sumOf { it.qty }
        val ret = b.returns.sumOf { r -> r.lines.sumOf { it.qty } }
        if (ret == 0) return "active"
        if (ret < total) return "partial"
        return if (b.returns.any { it.cancel }) "cancelled" else "returned"
    }

    fun billNet(b: Bill): Long = b.tot.total - b.returns.sumOf { it.amt }

    fun retSumsForLine(b: Bill, idx: Int): Triple<Int, Long, Long> {
        var q = 0
        var amt = 0L
        var tax = 0L
        for (r in b.returns) {
            for (l in r.lines) {
                if (l.idx == idx) {
                    q += l.qty
                    amt += l.amt
                    tax += l.tax
                }
            }
        }
        return Triple(q, amt, tax)
    }
}
