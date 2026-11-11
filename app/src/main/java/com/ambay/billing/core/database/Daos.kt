package com.ambay.billing.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deleted = 0")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE (code = :code OR fac = :code) AND deleted = 0 LIMIT 1")
    suspend fun findExactMatch(code: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("UPDATE items SET qty = qty + :delta WHERE id = :itemId")
    suspend fun adjustStock(itemId: String, delta: Int)

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int
}

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<SaleLineEntity>)

    @Transaction
    @Query("SELECT * FROM sales ORDER BY date DESC LIMIT :limit")
    fun getRecentSales(limit: Int = 100): Flow<List<SaleWithLines>>

    @Transaction
    @Query("SELECT * FROM sales WHERE date >= :fromTs AND date <= :toTs ORDER BY date DESC")
    fun getSalesByDateRange(fromTs: Long, toTs: Long): Flow<List<SaleWithLines>>

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleWithLines(saleId: String): SaleWithLines?

    @Transaction
    @Query("SELECT * FROM sales WHERE syncStatus = 'pending' ORDER BY date ASC LIMIT :limit")
    suspend fun getPendingSales(limit: Int = 100): List<SaleWithLines>

    @Query("UPDATE sales SET syncStatus = :status WHERE id = :saleId")
    suspend fun updateSyncStatus(saleId: String, status: String)

    @Query("UPDATE sales SET voidTs = :voidTs, voidReason = :reason, updatedAt = :updatedAt, syncStatus = 'pending' WHERE id = :saleId")
    suspend fun voidSale(saleId: String, voidTs: Long, reason: String, updatedAt: Long)

    @Query("SELECT COALESCE(MAX(CAST(SUBSTR(no, 4) AS INTEGER)), 0) FROM sales WHERE no LIKE 'WB-%'")
    suspend fun getMaxWbNumber(): Int
}

@Dao
interface StockMovementDao {
    @Query("SELECT 1 FROM stock_movements WHERE id = :movementId LIMIT 1")
    suspend fun hasMovement(movementId: String): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovement(movement: StockMovementEntity): Long
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingsEntity)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun removeSetting(key: String)
}
