package com.ambay.billing.core.model

data class PushSalesRequest(
    val txns: List<Sale>
)

data class PushResultItem(
    val id: String,
    val result: String,                // "created" | "duplicate" | "conflict" | "invalid" | "voided"
    val reason: String? = null,
    val warning: String? = null
)

data class SyncPushResponse(
    val type: String = "syncPush",
    val nonce: String,
    val results: List<PushResultItem>
)

data class SyncPullResponse(
    val type: String = "syncPull",
    val nonce: String,
    val sales: List<Sale>,
    val nextSeq: Long,
    val more: Boolean
)

data class HealthResponse(
    val type: String = "health",
    val nonce: String,
    val ok: Boolean,
    val serverTs: Long
)
