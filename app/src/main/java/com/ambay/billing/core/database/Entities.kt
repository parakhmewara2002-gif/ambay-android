package com.ambay.billing.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ambay.billing.core.model.Customer
import com.ambay.billing.core.model.Item
import com.ambay.billing.core.model.Sale
import com.ambay.billing.core.model.SaleLine
import com.ambay.billing.core.model.SaleTotal
import com.ambay.billing.core.model.VoidInfo

@Entity(
    tableName = "items",
    indices = [
        Index("code"),
        Index("fac"),
        Index("name")
    ]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sn: String = "",
    val model: String = "",
    val code: String = "",
    val fac: String = "",
    val cat: String = "",
    val price: Long = 0L,
    val gst: Int = 0,
    val md: Long? = null,
    val ws: String = "N",
    val qty: Int = 0,
    val tracked: Boolean = true,
    val deleted: Boolean = false
) {
    fun toDomain() = Item(
        id = id,
        name = name,
        sn = sn,
        model = model,
        code = code,
        fac = fac,
        cat = cat,
        price = price,
        gst = gst,
        md = md,
        ws = ws,
        qty = qty,
        tracked = tracked,
        deleted = deleted
    )

    companion object {
        fun fromDomain(item: Item) = ItemEntity(
            id = item.id,
            name = item.name,
            sn = item.sn,
            model = item.model,
            code = item.code,
            fac = item.fac,
            cat = item.cat,
            price = item.price,
            gst = item.gst,
            md = item.md,
            ws = item.ws,
            qty = item.qty,
            tracked = item.tracked,
            deleted = item.deleted
        )
    }
}

@Entity(
    tableName = "sales",
    indices = [
        Index("date"),
        Index("syncStatus"),
        Index("no")
    ]
)
data class SaleEntity(
    @PrimaryKey val id: String,
    val no: String,
    val type: String = "without_bill",
    val date: Long,
    val pay: String = "Cash",
    val custName: String = "",
    val custPhone: String = "",
    val note: String = "",
    val dev: String = "",
    val did: String = "",
    val createdAt: Long = date,
    val updatedAt: Long = date,
    val ver: Int = 1,
    val voidTs: Long? = null,
    val voidReason: String? = null,
    val voidId: String? = null,
    val totQty: Int,
    val totOrig: Long,
    val totDisc: Long,
    val totTotal: Long,
    val syncStatus: String = "pending" // "pending" | "synced" | "conflict"
)

@Entity(
    tableName = "sale_lines",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("saleId"),
        Index("itemId")
    ]
)
data class SaleLineEntity(
    @PrimaryKey(autoGenerate = true) val lineId: Long = 0L,
    val saleId: String,
    val itemId: String,
    val name: String,
    val sn: String = "",
    val model: String = "",
    val qty: Int,
    val op: Long,
    val gross: Long,
    val disc: Long,
    val total: Long,
    val ws: String = "N",
    val war: String = ""
) {
    fun toDomain() = SaleLine(
        itemId = itemId,
        name = name,
        sn = sn,
        model = model,
        qty = qty,
        op = op,
        gross = gross,
        disc = disc,
        total = total,
        ws = ws,
        war = war
    )

    companion object {
        fun fromDomain(saleId: String, line: SaleLine) = SaleLineEntity(
            saleId = saleId,
            itemId = line.itemId,
            name = line.name,
            sn = line.sn,
            model = line.model,
            qty = line.qty,
            op = line.op,
            gross = line.gross,
            disc = line.disc,
            total = line.total,
            ws = line.ws,
            war = line.war
        )
    }
}

data class SaleWithLines(
    @androidx.room.Embedded val sale: SaleEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "saleId"
    )
    val lines: List<SaleLineEntity>
) {
    fun toDomain() = Sale(
        id = sale.id,
        no = sale.no,
        type = sale.type,
        date = sale.date,
        lines = lines.map { it.toDomain() },
        tot = SaleTotal(
            qty = sale.totQty,
            orig = sale.totOrig,
            disc = sale.totDisc,
            total = sale.totTotal
        ),
        pay = sale.pay,
        cust = Customer(name = sale.custName, phone = sale.custPhone),
        note = sale.note,
        dev = sale.dev,
        did = sale.did,
        createdAt = sale.createdAt,
        updatedAt = sale.updatedAt,
        ver = sale.ver,
        void = if (sale.voidTs != null) VoidInfo(
            ts = sale.voidTs,
            reason = sale.voidReason ?: "",
            id = sale.voidId ?: ""
        ) else null,
        syncStatus = sale.syncStatus
    )
}

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey val id: String,        // e.g. "wsale:<txn>:<lineIndex>"
    val saleId: String,
    val itemId: String,
    val delta: Int,                    // negative for sales, positive for void
    val timestamp: Long
)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
