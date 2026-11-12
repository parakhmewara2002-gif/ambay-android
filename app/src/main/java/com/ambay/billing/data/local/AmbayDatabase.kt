package com.ambay.billing.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        ItemFtsEntity::class,
        BillEntity::class,
        WSaleEntity::class,
        StockMoveEntity::class,
        PendingQueueEntity::class,
        CategoryEntity::class,
        AuditEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AmbayDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun billDao(): BillDao
    abstract fun wSaleDao(): WSaleDao
    abstract fun stockMoveDao(): StockMoveDao
    abstract fun pendingQueueDao(): PendingQueueDao
    abstract fun categoryDao(): CategoryDao
    abstract fun auditDao(): AuditDao
    abstract fun settingDao(): SettingDao
}
