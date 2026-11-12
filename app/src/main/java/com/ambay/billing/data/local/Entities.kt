package com.ambay.billing.data.local

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [
        Index(value = ["code"]),
        Index(value = ["cat"]),
        Index(value = ["deleted"])
    ]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sn: String,
    val model: String,
    val code: String,
    val fac: String,
    val cat: String,
    val desc: String,
    val price: Long,
    val cost: Long,
    val gst: Int?,
    val ws: String,
    val war: String,
    val swar: String,
    val md: Long?,
    val rl: Int?,
    val rq: Int?,
    val ser: Boolean,
    val tracked: Boolean,
    val qty: Int?,
    val deleted: Boolean,
    val deletedAt: Long,
    val restoredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val fv: Int,
    val rev: Int,
    val devId: String,
    val syncState: String,
    val syncFileId: String?,
    val syncedVer: Int,
    val syncAt: Long,
    val syncErr: String?
)

@Fts4(contentEntity = ItemEntity::class)
@Entity(tableName = "items_fts")
data class ItemFtsEntity(
    val name: String,
    val sn: String,
    val model: String,
    val code: String,
    val fac: String,
    val cat: String
)

@Entity(
    tableName = "bills",
    indices = [
        Index(value = ["no"]),
        Index(value = ["date"]),
        Index(value = ["status"])
    ]
)
data class BillEntity(
    @PrimaryKey val id: String,
    val no: String,
    val date: Long,
    val custName: String,
    val custPhone: String,
    val linesJson: String,
    val discType: String,
    val discVal: Long,
    val gstBp: Int,
    val gstMode: String,
    val taxType: String,
    val pay: String,
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
    val shopJson: String,
    val returnsJson: String,
    val sigJson: String?,
    val dsigJson: String?,
    val status: String,
    val ver: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val devId: String,
    val syncState: String,
    val syncFileId: String?,
    val syncedVer: Int,
    val syncAt: Long,
    val syncErr: String?
)

@Entity(
    tableName = "wsales",
    indices = [
        Index(value = ["no"]),
        Index(value = ["date"]),
        Index(value = ["syncState"])
    ]
)
data class WSaleEntity(
    @PrimaryKey val id: String,
    val no: String,
    val date: Long,
    val linesJson: String,
    val totQty: Int,
    val totOrig: Long,
    val totDisc: Long,
    val totTotal: Long,
    val pay: String,
    val custName: String,
    val custPhone: String,
    val note: String,
    val dev: String,
    val did: String,
    val createdAt: Long,
    val updatedAt: Long,
    val ver: Int,
    val voidJson: String?,
    val syncState: String,
    val syncFileId: String?,
    val syncedVer: Int,
    val syncAt: Long,
    val syncErr: String?
)

@Entity(
    tableName = "stock_moves",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["ts"]),
        Index(value = ["ref"])
    ]
)
data class StockMoveEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val type: String,
    val qty: Int,
    val ts: Long,
    val ref: String?,
    val refNo: String,
    val note: String,
    val cost: Long,
    val devId: String
)

@Entity(
    tableName = "pending_queue",
    indices = [
        Index(value = ["state", "nextAt"]),
        Index(value = ["kind", "rid"])
    ]
)
data class PendingQueueEntity(
    @PrimaryKey val id: String,
    val kind: String, // "bill" | "item" | "sale" | "snapshot" | "manifest"
    val rid: String,
    val ver: Int,
    val state: String, // "pending" | "failed"
    val attempts: Int,
    val nextAt: Long,
    val lastErr: String?,
    val createdAt: Long,
    val metaName: String?
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "audit_log", indices = [Index(value = ["ts"])])
data class AuditEntity(
    @PrimaryKey val id: String,
    val ts: Long,
    val type: String,
    val ref: String,
    val detail: String
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val valueJson: String
)
