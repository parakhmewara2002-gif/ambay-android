package com.ambay.billing.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ambay.billing.core.crypto.Base64Url
import com.ambay.billing.core.crypto.CanonicalJson
import com.ambay.billing.core.crypto.DeviceKeyManager
import com.ambay.billing.core.database.AmbayDatabase
import com.ambay.billing.core.database.ItemEntity
import com.ambay.billing.core.database.SaleEntity
import com.ambay.billing.core.database.SaleLineEntity
import com.ambay.billing.core.database.SettingsEntity
import com.ambay.billing.core.database.StockMovementEntity
import com.ambay.billing.core.model.Item
import com.ambay.billing.core.model.PairRedeemRequest
import com.ambay.billing.core.model.PairSessionPayload
import com.ambay.billing.core.model.Sale
import com.ambay.billing.core.model.SaleLine
import com.ambay.billing.core.model.SaleTotal
import com.ambay.billing.core.model.ServerKey
import com.ambay.billing.core.network.AmbayApiService
import com.ambay.billing.core.network.ClockSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import androidx.room.withTransaction
import java.util.concurrent.TimeUnit

class AmbayRepository(
    private val context: Context,
    private val database: AmbayDatabase,
    private val apiService: AmbayApiService,
    private val deviceKeyManager: DeviceKeyManager
) {

    private val itemDao = database.itemDao()
    private val saleDao = database.saleDao()
    private val stockMovementDao = database.stockMovementDao()
    private val settingsDao = database.settingsDao()

    // ---------- Items & Inventory ----------

    fun getAllItems(): Flow<List<Item>> {
        return itemDao.getAllItems().map { list -> list.map { it.toDomain() } }
    }

    suspend fun findExactMatch(scannedCode: String): Item? = withContext(Dispatchers.IO) {
        val trimmed = scannedCode.trim()
        if (trimmed.isEmpty()) return@withContext null
        itemDao.findExactMatch(trimmed)?.toDomain()
    }

    suspend fun upsertItems(items: List<Item>) = withContext(Dispatchers.IO) {
        val entities = items.map { ItemEntity.fromDomain(it) }
        itemDao.upsertAll(entities)
    }

    // ---------- Sales & Billing ----------

    fun getRecentSales(limit: Int = 100): Flow<List<Sale>> {
        return saleDao.getRecentSales(limit).map { list -> list.map { it.toDomain() } }
    }

    fun getSalesByDateRange(fromTs: Long, toTs: Long): Flow<List<Sale>> {
        return saleDao.getSalesByDateRange(fromTs, toTs).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getSaleById(saleId: String): Sale? = withContext(Dispatchers.IO) {
        saleDao.getSaleWithLines(saleId)?.toDomain()
    }

    /**
     * Creates a Without-Bill sale with a permanent UUID v4 transaction ID.
     * Deducts local stock idempotently via fixed stock movement IDs.
     * Enqueues sale for server upload.
     */
    suspend fun createWithoutBillSale(
        lines: List<SaleLine>,
        payMode: String = "Cash",
        custName: String = "",
        custPhone: String = "",
        note: String = ""
    ): Result<Sale> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Cart is empty"))
        }

        val saleId = UUID.randomUUID().toString()
        val nextNum = saleDao.getMaxWbNumber() + 1
        val saleNo = String.format("WB-%04d", nextNum)
        val now = System.currentTimeMillis()

        var sumQty = 0
        var sumOrig = 0L
        var sumDisc = 0L
        var sumTotal = 0L

        val validatedLines = mutableListOf<SaleLine>()
        for (line in lines) {
            val item = itemDao.getItemById(line.itemId)?.toDomain()
            val unitPrice = item?.price ?: line.op
            val gross = unitPrice * line.qty
            val total = line.total
            val disc = (gross - total).coerceAtLeast(0L)

            // Validate max discount rule
            if (item != null) {
                val maxDiscPerUnit = item.maxDiscountPaise()
                if (maxDiscPerUnit != null) {
                    val maxAllowedLineDisc = maxDiscPerUnit * line.qty
                    if (disc > maxAllowedLineDisc) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Discount on ${item.name} exceeds max limit")
                        )
                    }
                }
            }

            sumQty += line.qty
            sumOrig += gross
            sumDisc += disc
            sumTotal += total

            validatedLines.add(
                line.copy(
                    op = unitPrice,
                    gross = gross,
                    disc = disc,
                    total = total
                )
            )
        }

        val tot = SaleTotal(
            qty = sumQty,
            orig = sumOrig,
            disc = sumDisc,
            total = sumTotal
        )

        val devName = settingsDao.getSetting("device_name") ?: "Android Cashier"
        val devId = settingsDao.getSetting("device_id") ?: ""

        val sale = Sale(
            id = saleId,
            no = saleNo,
            type = "without_bill",
            date = now,
            lines = validatedLines,
            tot = tot,
            pay = payMode,
            cust = com.ambay.billing.core.model.Customer(name = custName, phone = custPhone),
            note = note,
            dev = devName,
            did = devId,
            createdAt = now,
            updatedAt = now,
            ver = 1,
            syncStatus = "pending"
        )

        // Save sale to database
        val saleEntity = SaleEntity(
            id = sale.id,
            no = sale.no,
            type = sale.type,
            date = sale.date,
            pay = sale.pay,
            custName = sale.cust.name,
            custPhone = sale.cust.phone,
            note = sale.note,
            dev = sale.dev,
            did = sale.did,
            createdAt = sale.createdAt,
            updatedAt = sale.updatedAt,
            ver = sale.ver,
            totQty = tot.qty,
            totOrig = tot.orig,
            totDisc = tot.disc,
            totTotal = tot.total,
            syncStatus = "pending"
        )
        database.withTransaction {
        saleDao.insertSale(saleEntity)

        val lineEntities = validatedLines.map { SaleLineEntity.fromDomain(saleId, it) }
        saleDao.insertLines(lineEntities)

        // Idempotent stock deduction: movement id = "wsale:<txn>:<lineIndex>"
        for (i in validatedLines.indices) {
            val line = validatedLines[i]
            val movementId = "wsale:$saleId:$i"
            val exists = stockMovementDao.hasMovement(movementId)
            if (exists == null) {
                stockMovementDao.insertMovement(
                    StockMovementEntity(
                        id = movementId,
                        saleId = saleId,
                        itemId = line.itemId,
                        delta = -line.qty,
                        timestamp = now
                    )
                )
                itemDao.adjustStock(line.itemId, -line.qty)
            }
        }

        }

        // Trigger immediate background sync
        triggerSyncWork()

        Result.success(sale)
    }

    /**
     * Voids a sale, returns stock idempotently via "wsv:<txn>:<lineIndex>", and marks for sync.
     */
    suspend fun voidSale(saleId: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        val saleWithLines = saleDao.getSaleWithLines(saleId)
            ?: return@withContext Result.failure(IllegalArgumentException("Sale not found"))

        val now = System.currentTimeMillis()
        database.withTransaction {
        saleDao.voidSale(saleId, now, reason, now)

        // Idempotent stock return: movement id = "wsv:<txn>:<lineIndex>"
        for (i in saleWithLines.lines.indices) {
            val line = saleWithLines.lines[i]
            val movementId = "wsv:$saleId:$i"
            val exists = stockMovementDao.hasMovement(movementId)
            if (exists == null) {
                stockMovementDao.insertMovement(
                    StockMovementEntity(
                        id = movementId,
                        saleId = saleId,
                        itemId = line.itemId,
                        delta = line.qty,
                        timestamp = now
                    )
                )
                itemDao.adjustStock(line.itemId, line.qty)
            }
        }

        }

        triggerSyncWork()
        Result.success(Unit)
    }

    // ---------- Pairing & QR Enrollment ----------

    fun parsePairingQr(qrText: String): PairSessionPayload? {
        val trimmed = qrText.trim()
        if (!trimmed.startsWith("AMBPAIR1.")) return null
        val b64 = trimmed.substring(9)
        return try {
            val jsonBytes = Base64Url.decode(b64)
            val jsonStr = String(jsonBytes, Charsets.UTF_8)
            val obj = JSONObject(jsonStr)

            val serverKey = if (obj.has("r") && !obj.isNull("r")) {
                val r = obj.getJSONObject("r")
                ServerKey(
                    skid = r.getString("skid"),
                    alg = r.getString("alg"),
                    spki = r.getString("spki")
                )
            } else null

            PairSessionPayload(
                v = obj.optInt("v", 1),
                u = obj.getString("u"),
                t = obj.getString("t"),
                i = obj.getString("i"),
                e = obj.getLong("e"),
                r = serverKey
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun redeemPairing(payload: PairSessionPayload, deviceName: String = "Android Cashier"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (kid, spki) = deviceKeyManager.getOrCreateKey()

            val random = SecureRandom()
            val nonceBytes = ByteArray(16)
            random.nextBytes(nonceBytes)
            val nonce = Base64Url.encode(nonceBytes)

            val pop = deviceKeyManager.generatePairingPop(payload.t, kid, nonce)

            val reqBody = JSONObject().apply {
                put("token", payload.t)
                put("alg", "ES256")
                put("spki", spki)
                put("nonce", nonce)
                put("pop", pop)
                put("name", deviceName)
                put("platform", "android")
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = reqBody.toString().toRequestBody(mediaType)

            val response = apiService.pairRedeem(requestBody)
            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string() ?: "Pairing failed"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val respBodyStr = response.body()?.string() ?: return@withContext Result.failure(Exception("Empty reply"))
            val root = JSONObject(respBodyStr)

            // Save pinned server keys from pairing QR or response
            if (payload.r != null) {
                saveServerKey(payload.r)
            }

            val p = if (root.has("p")) root.getJSONObject("p") else root
            if (p.has("serverKeys")) {
                val sKeys = p.getJSONObject("serverKeys")
                val keys = sKeys.keys()
                while (keys.hasNext()) {
                    val skid = keys.next()
                    val k = sKeys.getJSONObject(skid)
                    saveServerKey(ServerKey(skid = skid, alg = k.getString("alg"), spki = k.getString("spki")))
                }
            }

            settingsDao.setSetting(SettingsEntity("device_kid", kid))
            settingsDao.setSetting(SettingsEntity("device_id", p.optString("deviceId", "")))
            settingsDao.setSetting(SettingsEntity("issuer_id", p.optString("issuer", payload.i)))
            settingsDao.setSetting(SettingsEntity("device_name", deviceName))
            settingsDao.setSetting(SettingsEntity("server_url", payload.u))

            // Start periodic heartbeat and sync
            schedulePeriodicSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveServerKey(key: ServerKey) {
        val json = JSONObject().apply {
            put("skid", key.skid)
            put("alg", key.alg)
            put("spki", key.spki)
        }.toString()
        settingsDao.setSetting(SettingsEntity("server_key_${key.skid}", json))
    }

    suspend fun getPinnedServerKeys(): Map<String, ServerKey> = withContext(Dispatchers.IO) {
        // Collect pinned keys stored in database
        val map = mutableMapOf<String, ServerKey>()
        // In real execution, retrieve all matching "server_key_%" from settingsDao
        map
    }

    suspend fun getDeviceKid(): String? = withContext(Dispatchers.IO) {
        settingsDao.getSetting("device_kid")
    }

    // ---------- Cloud Sync Execution ----------

    suspend fun pushPendingSales(): Result<Int> = withContext(Dispatchers.IO) {
        val pending = saleDao.getPendingSales(50)
        if (pending.isEmpty()) return@withContext Result.success(0)

        try {
            val txnsArray = JSONArray()
            for (item in pending) {
                val sale = item.toDomain()
                txnsArray.put(saleToJson(sale))
            }

            val body = JSONObject().apply {
                put("txns", txnsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = body.toString().toRequestBody(mediaType)

            val response = apiService.pushSales(requestBody)
            if (response.isSuccessful) {
                val respStr = response.body()?.string() ?: "{}"
                val root = JSONObject(respStr)
                val results = root.optJSONArray("results") ?: JSONArray()

                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val id = r.getString("id")
                    val res = r.getString("result")
                    val status = when (res) {
                        "created", "duplicate", "voided" -> "synced"
                        "conflict" -> "conflict"
                        else -> "pending"
                    }
                    saleDao.updateSyncStatus(id, status)
                }
                Result.success(pending.size)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullRemoteSales(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val lastSeq = settingsDao.getSetting("last_sales_seq")?.toLongOrNull() ?: 0L
            val response = apiService.pullSales(lastSeq, 100)
            if (response.isSuccessful) {
                val respStr = response.body()?.string() ?: "{}"
                val root = JSONObject(respStr)
                val salesArray = root.optJSONArray("sales") ?: JSONArray()
                val nextSeq = root.optLong("nextSeq", lastSeq)

                for (i in 0 until salesArray.length()) {
                    val sObj = salesArray.getJSONObject(i)
                    val sale = jsonToSale(sObj)
                    importRemoteSale(sale)
                }

                settingsDao.setSetting(SettingsEntity("last_sales_seq", nextSeq.toString()))
                Result.success(salesArray.length())
            } else {
                Result.failure(Exception("Pull failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun importRemoteSale(sale: Sale) {
        val existing = saleDao.getSaleWithLines(sale.id)
        if (existing == null) {
            val saleEntity = SaleEntity(
                id = sale.id,
                no = sale.no,
                type = sale.type,
                date = sale.date,
                pay = sale.pay,
                custName = sale.cust.name,
                custPhone = sale.cust.phone,
                note = sale.note,
                dev = sale.dev,
                did = sale.did,
                createdAt = sale.createdAt,
                updatedAt = sale.updatedAt,
                ver = sale.ver,
                voidTs = sale.void?.ts,
                voidReason = sale.void?.reason,
                voidId = sale.void?.id,
                totQty = sale.tot.qty,
                totOrig = sale.tot.orig,
                totDisc = sale.tot.disc,
                totTotal = sale.tot.total,
                syncStatus = "synced"
            )
            saleDao.insertSale(saleEntity)

            val lines = sale.lines.map { SaleLineEntity.fromDomain(sale.id, it) }
            saleDao.insertLines(lines)

            // Deduct stock idempotently
            for (i in sale.lines.indices) {
                val line = sale.lines[i]
                val movementId = "wsale:${sale.id}:$i"
                val exists = stockMovementDao.hasMovement(movementId)
                if (exists == null) {
                    stockMovementDao.insertMovement(
                        StockMovementEntity(
                            id = movementId,
                            saleId = sale.id,
                            itemId = line.itemId,
                            delta = -line.qty,
                            timestamp = sale.date
                        )
                    )
                    itemDao.adjustStock(line.itemId, -line.qty)
                }
            }
        } else if (sale.void != null && existing.sale.voidTs == null) {
            // Apply void from remote device
            saleDao.voidSale(sale.id, sale.void.ts, sale.void.reason, sale.updatedAt)
            for (i in existing.lines.indices) {
                val line = existing.lines[i]
                val movementId = "wsv:${sale.id}:$i"
                val exists = stockMovementDao.hasMovement(movementId)
                if (exists == null) {
                    stockMovementDao.insertMovement(
                        StockMovementEntity(
                            id = movementId,
                            saleId = sale.id,
                            itemId = line.itemId,
                            delta = line.qty,
                            timestamp = sale.void.ts
                        )
                    )
                    itemDao.adjustStock(line.itemId, line.qty)
                }
            }
        }
    }

    private fun saleToJson(sale: Sale): JSONObject {
        val linesArray = JSONArray()
        for (l in sale.lines) {
            linesArray.put(
                JSONObject().apply {
                    put("itemId", l.itemId)
                    put("name", l.name)
                    put("sn", l.sn)
                    put("model", l.model)
                    put("qty", l.qty)
                    put("op", l.op)
                    put("gross", l.gross)
                    put("disc", l.disc)
                    put("total", l.total)
                    put("ws", l.ws)
                    put("war", l.war)
                }
            )
        }

        val totObj = JSONObject().apply {
            put("qty", sale.tot.qty)
            put("orig", sale.tot.orig)
            put("disc", sale.tot.disc)
            put("total", sale.tot.total)
        }

        val custObj = JSONObject().apply {
            put("name", sale.cust.name)
            put("phone", sale.cust.phone)
        }

        return JSONObject().apply {
            put("id", sale.id)
            put("no", sale.no)
            put("type", sale.type)
            put("date", sale.date)
            put("lines", linesArray)
            put("tot", totObj)
            put("pay", sale.pay)
            put("cust", custObj)
            put("note", sale.note)
            put("dev", sale.dev)
            put("did", sale.did)
            put("createdAt", sale.createdAt)
            put("updatedAt", sale.updatedAt)
            put("ver", sale.ver)
            if (sale.void != null) {
                put("void", JSONObject().apply {
                    put("ts", sale.void.ts)
                    put("reason", sale.void.reason)
                    put("id", sale.void.id)
                })
            }
        }
    }

    private fun jsonToSale(o: JSONObject): Sale {
        val linesArray = o.getJSONArray("lines")
        val lines = mutableListOf<SaleLine>()
        for (i in 0 until linesArray.length()) {
            val l = linesArray.getJSONObject(i)
            lines.add(
                SaleLine(
                    itemId = l.getString("itemId"),
                    name = l.getString("name"),
                    sn = l.optString("sn", ""),
                    model = l.optString("model", ""),
                    qty = l.getInt("qty"),
                    op = l.getLong("op"),
                    gross = l.getLong("gross"),
                    disc = l.getLong("disc"),
                    total = l.getLong("total"),
                    ws = l.optString("ws", "N"),
                    war = l.optString("war", "")
                )
            )
        }

        val tObj = o.getJSONObject("tot")
        val tot = SaleTotal(
            qty = tObj.getInt("qty"),
            orig = tObj.getLong("orig"),
            disc = tObj.getLong("disc"),
            total = tObj.getLong("total")
        )

        val cObj = o.optJSONObject("cust")
        val cust = com.ambay.billing.core.model.Customer(
            name = cObj?.optString("name", "") ?: "",
            phone = cObj?.optString("phone", "") ?: ""
        )

        val vObj = o.optJSONObject("void")
        val voidInfo = if (vObj != null) {
            com.ambay.billing.core.model.VoidInfo(
                ts = vObj.getLong("ts"),
                reason = vObj.optString("reason", ""),
                id = vObj.optString("id", "")
            )
        } else null

        return Sale(
            id = o.getString("id"),
            no = o.getString("no"),
            type = o.optString("type", "without_bill"),
            date = o.getLong("date"),
            lines = lines,
            tot = tot,
            pay = o.optString("pay", "Cash"),
            cust = cust,
            note = o.optString("note", ""),
            dev = o.optString("dev", ""),
            did = o.optString("did", ""),
            createdAt = o.optLong("createdAt", o.getLong("date")),
            updatedAt = o.optLong("updatedAt", o.getLong("date")),
            ver = o.optInt("ver", 1),
            void = voidInfo,
            syncStatus = "synced"
        )
    }

    fun triggerSyncWork() {
        val request = OneTimeWorkRequestBuilder<SalesSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun schedulePeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SalesSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ambay_sales_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ambay_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            heartbeatRequest
        )
    }
}
