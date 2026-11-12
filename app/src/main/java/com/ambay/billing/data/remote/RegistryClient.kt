package com.ambay.billing.data.remote

import com.ambay.billing.core.CanonicalJson
import com.ambay.billing.core.LinkedDevice
import com.ambay.billing.core.PairingQrPayload
import com.ambay.billing.core.ServerEnvelope
import com.ambay.billing.core.ServerKeyEntry
import com.ambay.billing.core.TrustBundle
import com.ambay.billing.core.TrustCrypto
import com.ambay.billing.core.TrustedKeyEntry
import com.ambay.billing.data.local.SecurePrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

class RegistryException(
    val kind: String,
    override val message: String,
    val code: String? = null,
    val httpStatus: Int = 0
) : Exception(message)

class DeviceRevokedException(
    override val message: String = "Is device ko owner ne logout kar diya"
) : Exception(message)

@Serializable
data class PushSaleResult(
    val id: String,
    val result: String,
    val warning: String? = null,
    val reason: String? = null
)

@Serializable
data class PullSalesResponse(
    val sales: List<JsonObject>,
    val nextSeq: Long,
    val more: Boolean
)

@Serializable
data class PullItemsResponse(
    val items: List<JsonObject>,
    val nextSeq: Long,
    val more: Boolean
)

@Singleton
class RegistryClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val securePrefs: SecurePrefs
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(): String = securePrefs.getBaseUrl().trimEnd('/')

    private fun executeAndVerify(
        request: Request,
        expectedType: String,
        expectedNonce: String,
        pinnedOverride: Map<String, ServerKeyEntry>? = null
    ): JsonObject {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: Throwable) {
            throw RegistryException("network", e.message ?: "Server not reachable")
        }

        val bodyStr = response.body?.string().orEmpty()
        if (bodyStr.length > 1_048_576) {
            throw RegistryException("invalid_response", "Response too large", httpStatus = response.code)
        }

        if (response.code == 401 || response.code == 403) {
            val errObj = runCatching { json.parseToJsonElement(bodyStr).jsonObject }.getOrNull()
            val errCode = errObj?.get("error")?.jsonPrimitive?.contentOrNull
            val errMsg = errObj?.get("message")?.jsonPrimitive?.contentOrNull ?: "Not authorised"
            if (errCode == "key_revoked" || errCode == "device_logged_out") {
                securePrefs.wipePairingSession()
                throw DeviceRevokedException("Is device ko owner ne logout kar diya")
            }
            throw RegistryException("auth", errMsg, code = errCode, httpStatus = response.code)
        }

        if (response.code == 429) {
            throw RegistryException("rate", "Too many requests", httpStatus = 429)
        }

        val envelope = runCatching { json.decodeFromString<ServerEnvelope>(bodyStr) }.getOrNull()
            ?: if (response.code >= 400) {
                throw RegistryException("http", "Server error ${response.code}", httpStatus = response.code)
            } else {
                throw RegistryException("invalid_response", "Unsigned or malformed server response rejected", httpStatus = response.code)
            }

        val keys = pinnedOverride ?: securePrefs.getServerKeys()
        if (keys.isEmpty()) {
            throw RegistryException("config", "Server trust root is not pinned")
        }

        return when (val v = TrustCrypto.verifyEnvelope(envelope, keys, expectedType, expectedNonce)) {
            is com.ambay.billing.core.EnvelopeVerifyResult.Valid -> {
                val offset = v.serverTs - System.currentTimeMillis()
                securePrefs.setClockOffsetMs(offset)
                v.payload
            }
            is com.ambay.billing.core.EnvelopeVerifyResult.Invalid -> {
                throw RegistryException("invalid_response", "Envelope verification failed: ${v.reason}", httpStatus = response.code)
            }
        }
    }

    private fun signedRequest(
        method: String,
        pathAndQuery: String,
        bodyJson: String? = null,
        expectedType: String,
        retryClockSkew: Boolean = true
    ): JsonObject {
        val kid = securePrefs.getDeviceKid() ?: throw RegistryException("config", "Device is not paired")
        val nonce = TrustCrypto.randomB64u(16)
        val ts = (System.currentTimeMillis() + securePrefs.getClockOffsetMs()).toString()
        val bodyStr = bodyJson ?: ""
        val msg = TrustCrypto.reqMessage(method, pathAndQuery, bodyStr, ts, nonce)
        val sig = securePrefs.signWithDeviceKey(kid, msg)

        val builder = Request.Builder()
            .url(baseUrl() + pathAndQuery)
            .header("Accept", "application/json")
            .header("X-Amb-Kid", kid)
            .header("X-Amb-Ts", ts)
            .header("X-Amb-Nonce", nonce)
            .header("X-Amb-Sig", sig)

        if (method.uppercase() == "POST") {
            builder.post(bodyStr.toRequestBody(jsonMedia))
        } else {
            builder.get()
        }

        return try {
            executeAndVerify(builder.build(), expectedType, nonce)
        } catch (e: RegistryException) {
            if (retryClockSkew && e.kind == "auth" && e.code == "clock_skew") {
                health()
                signedRequest(method, pathAndQuery, bodyJson, expectedType, retryClockSkew = false)
            } else {
                throw e
            }
        }
    }

    fun health(): Long {
        val nonce = TrustCrypto.randomB64u(16)
        val req = Request.Builder()
            .url("${baseUrl()}/v1/health?nonce=$nonce")
            .header("Accept", "application/json")
            .get()
            .build()
        val p = executeAndVerify(req, "health", nonce)
        return p["serverTs"]!!.jsonPrimitive.longOrNull ?: System.currentTimeMillis()
    }

    fun redeemPairing(qr: PairingQrPayload, deviceName: String): Pair<String, String> {
        val serverUrl = qr.u.trimEnd('/')
        securePrefs.setBaseUrl(serverUrl)
        val pinned = mapOf(qr.r.skid to ServerKeyEntry(qr.r.alg, qr.r.spki, "active"))
        securePrefs.setServerKeys(pinned)

        val keyInfo = securePrefs.ensureDeviceKey(forceNew = false)
        val nonce = TrustCrypto.randomB64u(16)
        val popMsg = TrustCrypto.enrollMessage("pair:${qr.t}", keyInfo.kid, nonce)
        val pop = securePrefs.signWithDeviceKey(keyInfo.kid, popMsg)

        val body = CanonicalJson.stringify(
            mapOf(
                "token" to qr.t,
                "alg" to keyInfo.alg,
                "spki" to keyInfo.spkiB64u,
                "nonce" to nonce,
                "pop" to pop,
                "name" to deviceName,
                "platform" to "android"
            )
        )
        val req = Request.Builder()
            .url("$serverUrl/v1/pair/redeem")
            .header("Accept", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()

        val p = executeAndVerify(req, "pair", nonce, pinnedOverride = pinned)
        val issuer = p["issuer"]?.jsonPrimitive?.content.orEmpty()
        val deviceId = p["deviceId"]?.jsonPrimitive?.content.orEmpty()
        val role = p["role"]?.jsonPrimitive?.content ?: "cashier"
        val kid = p["kid"]?.jsonPrimitive?.content ?: keyInfo.kid

        // Pin any rotated/additional serverKeys returned in the signed pair payload
        val sKeysObj = p["serverKeys"] as? JsonObject
        if (sKeysObj != null) {
            val map = mutableMapOf<String, ServerKeyEntry>()
            for ((skid, el) in sKeysObj) {
                val o = el.jsonObject
                map[skid] = ServerKeyEntry(
                    alg = o["alg"]?.jsonPrimitive?.content ?: "ES256",
                    spki = o["spki"]?.jsonPrimitive?.content ?: "",
                    status = o["status"]?.jsonPrimitive?.content ?: "active"
                )
            }
            if (map.isNotEmpty()) securePrefs.setServerKeys(map)
        }

        securePrefs.setPairingSession(kid, deviceId, issuer, role)
        return kid to deviceId
    }

    fun pushSales(txns: List<JsonObject>): List<PushSaleResult> {
        val body = JsonObject(mapOf("txns" to JsonArray(txns))).toString()
        val p = signedRequest("POST", "/v1/sync/sales", body, "syncPush")
        val arr = p["results"]?.jsonArray ?: return emptyList()
        return arr.map { el ->
            val o = el.jsonObject
            PushSaleResult(
                id = o["id"]?.jsonPrimitive?.content.orEmpty(),
                result = o["result"]?.jsonPrimitive?.content.orEmpty(),
                warning = o["warning"]?.jsonPrimitive?.contentOrNull,
                reason = o["reason"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    fun pullSales(afterSeq: Long): PullSalesResponse {
        val p = signedRequest("GET", "/v1/sync/sales?after=$afterSeq", null, "syncPull")
        val sales = p["sales"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val nextSeq = p["nextSeq"]?.jsonPrimitive?.longOrNull ?: afterSeq
        val more = p["more"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        return PullSalesResponse(sales, nextSeq, more)
    }

    fun pullItems(afterSeq: Long): PullItemsResponse {
        val p = signedRequest("GET", "/v1/sync/items?after=$afterSeq", null, "syncItems")
        val items = p["items"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val nextSeq = p["nextSeq"]?.jsonPrimitive?.longOrNull ?: afterSeq
        val more = p["more"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        return PullItemsResponse(items, nextSeq, more)
    }

    fun heartbeat() {
        signedRequest("POST", "/v1/devices/heartbeat", "{}", "heartbeat")
    }

    fun listDevices(): List<LinkedDevice> {
        val p = signedRequest("GET", "/v1/devices", null, "devices")
        return p["devices"]?.jsonArray?.map { el ->
            val o = el.jsonObject
            LinkedDevice(
                kid = o["kid"]?.jsonPrimitive?.content.orEmpty(),
                deviceId = o["deviceId"]?.jsonPrimitive?.content.orEmpty(),
                name = o["name"]?.jsonPrimitive?.content.orEmpty(),
                platform = o["platform"]?.jsonPrimitive?.content.orEmpty(),
                status = o["status"]?.jsonPrimitive?.content.orEmpty(),
                connection = o["connection"]?.jsonPrimitive?.content.orEmpty(),
                pairedAt = o["pairedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                lastActive = o["lastActive"]?.jsonPrimitive?.longOrNull ?: 0L,
                lastSync = o["lastSync"]?.jsonPrimitive?.longOrNull ?: 0L,
                revokedAt = o["revokedAt"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        }.orEmpty()
    }

    fun logoutDevice(kid: String, reason: String = "logout") {
        val body = CanonicalJson.stringify(mapOf("reason" to reason))
        signedRequest("POST", "/v1/devices/$kid/logout", body, "result")
    }

    fun createPairingSession(): JsonObject =
        signedRequest("POST", "/v1/pair/sessions", "{}", "pairSession")

    fun fetchTrustBundle(issuer: String): TrustBundle {
        val nonce = TrustCrypto.randomB64u(16)
        val req = Request.Builder()
            .url("${baseUrl()}/v1/trust?issuer=$issuer&nonce=$nonce")
            .header("Accept", "application/json")
            .get()
            .build()
        val p = executeAndVerify(req, "trust", nonce)
        val seq = p["seq"]?.jsonPrimitive?.longOrNull ?: 0L
        val cur = securePrefs.getTrustBundle()
        if (cur != null && seq < cur.seq) {
            throw RegistryException("invalid_response", "Trust bundle rollback refused")
        }
        val keysMap = mutableMapOf<String, TrustedKeyEntry>()
        (p["keys"] as? JsonObject)?.forEach { (k, el) ->
            val o = el.jsonObject
            keysMap[k] = TrustedKeyEntry(
                alg = o["alg"]?.jsonPrimitive?.content ?: "ES256",
                spki = o["spki"]?.jsonPrimitive?.content ?: "",
                status = o["status"]?.jsonPrimitive?.content ?: "active",
                revokedAt = o["revokedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                issuer = o["issuer"]?.jsonPrimitive?.content ?: ""
            )
        }
        val srvMap = securePrefs.getServerKeys().toMutableMap()
        (p["serverKeys"] as? JsonObject)?.forEach { (k, el) ->
            val o = el.jsonObject
            if (o["status"]?.jsonPrimitive?.content == "revoked") {
                srvMap.remove(k)
            } else {
                srvMap[k] = ServerKeyEntry(
                    alg = o["alg"]?.jsonPrimitive?.content ?: "ES256",
                    spki = o["spki"]?.jsonPrimitive?.content ?: ""
                )
            }
        }
        val bundle = TrustBundle(seq = seq, keys = keysMap, serverKeys = srvMap, issuedAt = p["serverTs"]?.jsonPrimitive?.longOrNull ?: 0L)
        securePrefs.setTrustBundle(bundle)
        return bundle
    }

    fun verifyPublicBill(uuid: String, digest: String): JsonObject {
        val nonce = TrustCrypto.randomB64u(16)
        val req = Request.Builder()
            .url("${baseUrl()}/v1/verify/$uuid?nonce=$nonce&digest=$digest")
            .header("Accept", "application/json")
            .get()
            .build()
        return executeAndVerify(req, "status", nonce)
    }
}
