package com.ambay.billing.data.repository

import android.os.Build
import com.ambay.billing.core.BackupConverter
import com.ambay.billing.core.TrustCrypto
import com.ambay.billing.core.WSale
import com.ambay.billing.data.local.AmbayDatabase
import com.ambay.billing.data.local.SecurePrefs
import com.ambay.billing.data.remote.DeviceRevokedException
import com.ambay.billing.data.remote.RegistryClient
import com.ambay.billing.data.remote.ServerCatalogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSummary(
    val pushed: Int = 0,
    val pulledSales: Int = 0,
    val pulledItems: Int = 0,
    val revoked: Boolean = false,
    val error: String? = null
)

@Singleton
class SyncRepository @Inject constructor(
    private val db: AmbayDatabase,
    private val billingRepo: BillingRepository,
    private val registryClient: RegistryClient,
    private val serverCatalogSource: ServerCatalogSource,
    private val securePrefs: SecurePrefs
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun pairWithQrText(qrText: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = TrustCrypto.parsePairingQr(qrText)
                ?: error("Ye valid AMBAY pairing QR code nahi hai")
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val (kid, _) = registryClient.redeemPairing(payload, deviceName)
            kid
        }
    }

    suspend fun syncNow(): SyncSummary = withContext(Dispatchers.IO) {
        if (securePrefs.getDeviceKid() == null || securePrefs.isDeviceRevoked()) {
            return@withContext SyncSummary(revoked = securePrefs.isDeviceRevoked())
        }
        try {
            registryClient.heartbeat()

            // 1. Push pending Without-Bill sales (<= 100 per batch)
            val pending = db.wSaleDao().getPendingSync(100)
            var pushedCount = 0
            if (pending.isNotEmpty()) {
                val txns = pending.map { ent ->
                    val w = WSale(
                        id = ent.id,
                        no = ent.no,
                        date = ent.date,
                        lines = json.decodeFromString(ent.linesJson),
                        tot = com.ambay.billing.core.WSaleTotals(ent.totQty, ent.totOrig, ent.totDisc, ent.totTotal),
                        pay = ent.pay,
                        cust = com.ambay.billing.core.CustomerInfo(ent.custName, ent.custPhone),
                        note = ent.note,
                        dev = ent.dev,
                        did = ent.did,
                        createdAt = ent.createdAt,
                        updatedAt = ent.updatedAt,
                        ver = ent.ver,
                        voidInfo = ent.voidJson?.let { json.decodeFromString(it) }
                    )
                    json.encodeToJsonElement(w).jsonObject
                }
                val results = registryClient.pushSales(txns)
                val now = System.currentTimeMillis()
                for (r in results) {
                    val state = when (r.result) {
                        "created", "duplicate", "voided" -> "synced"
                        "conflict", "invalid" -> "conflict"
                        else -> "pending"
                    }
                    db.wSaleDao().updateSyncState(r.id, state, now, r.reason)
                    if (state == "synced") pushedCount++
                }
            }

            // 2. Pull other devices' sales
            var seq = securePrefs.getSalesSyncSeq()
            var pulledSalesCount = 0
            do {
                val resp = registryClient.pullSales(seq)
                for (saleObj in resp.sales) {
                    val parsed = BackupConverter.parseBackupJson(
                        """{"schema":2,"type":"wsale","sale":$saleObj,"moves":[]}"""
                    ).getOrNull()
                    parsed?.wsales?.firstOrNull()?.let { w ->
                        billingRepo.importRemoteWSale(w)
                        pulledSalesCount++
                    }
                }
                seq = resp.nextSeq
                securePrefs.setSalesSyncSeq(seq)
            } while (resp.more)

            // 3. Pull items catalog if supported by server
            var pulledItemsCount = 0
            runCatching {
                val items = serverCatalogSource.fetchItems()
                if (items.isNotEmpty()) {
                    pulledItemsCount = items.size
                    for (it in items) {
                        billingRepo.upsertItem(it, forceDuplicate = true)
                    }
                }
            }

            SyncSummary(pushed = pushedCount, pulledSales = pulledSalesCount, pulledItems = pulledItemsCount)
        } catch (e: DeviceRevokedException) {
            SyncSummary(revoked = true, error = e.message)
        } catch (e: Throwable) {
            SyncSummary(error = e.message ?: "Sync failed")
        }
    }
}
