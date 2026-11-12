package com.ambay.billing.data.remote

import com.ambay.billing.core.BackupConverter
import com.ambay.billing.core.Item
import com.ambay.billing.data.local.SecurePrefs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

interface CatalogSource {
    suspend fun fetchItems(): List<Item>
}

@Singleton
class ServerCatalogSource @Inject constructor(
    private val registryClient: RegistryClient,
    private val securePrefs: SecurePrefs
) : CatalogSource {
    override suspend fun fetchItems(): List<Item> {
        var seq = securePrefs.getItemsSyncSeq()
        val collected = mutableListOf<Item>()
        do {
            val resp = registryClient.pullItems(seq)
            for (o in resp.items) {
                collected += parseServerItem(o)
            }
            seq = resp.nextSeq
            securePrefs.setItemsSyncSeq(seq)
        } while (resp.more)
        return collected
    }

    private fun parseServerItem(o: JsonObject): Item {
        val ws = o["ws"]?.jsonPrimitive?.contentOrNull ?: "N"
        return Item(
            id = o["id"]?.jsonPrimitive?.content.orEmpty(),
            name = o["name"]?.jsonPrimitive?.content.orEmpty(),
            sn = o["sn"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            model = o["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            code = o["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            fac = o["fac"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            cat = o["cat"]?.jsonPrimitive?.contentOrNull?.ifBlank { "Uncategorized" } ?: "Uncategorized",
            price = o["price"]?.jsonPrimitive?.longOrNull ?: 0L,
            cost = 0L, // cost NEVER reaches staff devices
            gst = o["gst"]?.jsonPrimitive?.intOrNull,
            md = o["md"]?.jsonPrimitive?.longOrNull,
            ws = ws,
            qty = o["qty"]?.jsonPrimitive?.intOrNull,
            tracked = o["tracked"]?.jsonPrimitive?.booleanOrNull ?: false,
            deleted = o["deleted"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    }
}

class LocalJsonCatalogSource(private val rawJson: String) : CatalogSource {
    override suspend fun fetchItems(): List<Item> {
        val parsed = BackupConverter.parseBackupJson(rawJson).getOrThrow()
        return parsed.items
    }
}
