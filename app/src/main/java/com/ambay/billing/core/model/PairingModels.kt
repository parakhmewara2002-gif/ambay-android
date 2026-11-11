package com.ambay.billing.core.model

data class ServerKey(
    val skid: String,
    val alg: String,
    val spki: String
)

data class PairSessionPayload(
    val v: Int = 1,
    val u: String,                     // Server URL
    val t: String,                     // One-time pairing token
    val i: String,                     // Issuer ID
    val e: Long,                       // Expiration timestamp in ms
    val r: ServerKey? = null           // Server trust key
)

data class PairRedeemRequest(
    val token: String,
    val alg: String = "ES256",
    val spki: String,                  // Base64URL SubjectPublicKeyInfo DER
    val nonce: String,                 // Random 16 bytes base64URL
    val pop: String,                   // Base64URL signature of enrollMessage
    val name: String = "Android Cashier",
    val platform: String = "android"
)

data class PairResult(
    val kid: String,
    val issuer: String,
    val deviceId: String,
    val role: String,
    val serverKeys: Map<String, ServerKey> = emptyMap()
)
