package com.ambay.billing.core.network

import com.ambay.billing.core.model.Item
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface CatalogSource {
    suspend fun getItems(): List<Item>
}

/**
 * Remote catalog source fetching items incrementally from /v1/sync/items?after=<seq>.
 */
class RemoteCatalogSource(
    private val apiService: AmbayApiService
) : CatalogSource {

    override suspend fun getItems(): List<Item> {
        return try {
            val response = apiService.syncItems(0L)
            if (response.isSuccessful) {
                val jsonStr = response.body()?.string() ?: return emptyList()
                parseItemsJson(jsonStr)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseItemsJson(jsonStr: String): List<Item> {
        val root = JSONObject(jsonStr)
        val array = root.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<Item>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(jsonToItem(o))
        }
        return list
    }

    private fun jsonToItem(o: JSONObject): Item {
        return Item(
            id = o.getString("id"),
            name = o.getString("name"),
            sn = o.optString("sn", ""),
            model = o.optString("model", ""),
            code = o.optString("code", ""),
            fac = o.optString("fac", ""),
            cat = o.optString("cat", ""),
            price = o.optLong("price", 0L),
            gst = o.optInt("gst", 0),
            md = if (o.has("md") && !o.isNull("md")) o.getLong("md") else null,
            ws = o.optString("ws", "N"),
            qty = o.optInt("qty", 0),
            tracked = o.optBoolean("tracked", true),
            deleted = o.optBoolean("deleted", false)
        )
    }
}

/**
 * Local JSON catalog importer reading from shop backup or inventory file export.
 */
class LocalJsonCatalogSource(
    private val jsonFile: File
) : CatalogSource {

    override suspend fun getItems(): List<Item> {
        if (!jsonFile.exists()) return emptyList()
        val text = jsonFile.readText(Charsets.UTF_8)
        return parseExportJson(text)
    }

    companion object {
        fun parseExportJson(jsonStr: String): List<Item> {
            val list = mutableListOf<Item>()
            val root = JSONObject(jsonStr)
            val itemsArray = if (root.has("items")) {
                root.getJSONArray("items")
            } else if (root.has("data") && root.getJSONObject("data").has("items")) {
                root.getJSONObject("data").getJSONArray("items")
            } else {
                JSONArray()
            }

            for (i in 0 until itemsArray.length()) {
                val o = itemsArray.getJSONObject(i)
                list.add(
                    Item(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        name = o.getString("name"),
                        sn = o.optString("sn", ""),
                        model = o.optString("model", ""),
                        code = o.optString("code", ""),
                        fac = o.optString("fac", ""),
                        cat = o.optString("cat", ""),
                        price = o.optLong("price", 0L),
                        gst = o.optInt("gst", 0),
                        md = if (o.has("md") && !o.isNull("md")) o.getLong("md") else null,
                        ws = o.optString("ws", "N"),
                        qty = o.optInt("qty", 0),
                        tracked = o.optBoolean("tracked", true),
                        deleted = o.optBoolean("deleted", false)
                    )
                )
            }
            return list
        }
    }
}
