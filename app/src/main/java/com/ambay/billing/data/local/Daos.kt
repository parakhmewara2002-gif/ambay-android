package com.ambay.billing.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deleted = 0 ORDER BY name ASC")
    fun observeActive(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY name ASC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY name ASC")
    suspend fun getAll(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE deleted = 0")
    suspend fun getActive(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ItemEntity?

    @Query(
        """
        SELECT items.* FROM items
        JOIN items_fts ON items.rowid = items_fts.rowid
        WHERE items.deleted = 0 AND items_fts MATCH :ftsQuery
        ORDER BY items.name ASC
        LIMIT 100
        """
    )
    suspend fun searchFts(ftsQuery: String): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("UPDATE items SET qty = :qty WHERE id = :id")
    suspend fun updateQty(id: String, qty: Int?)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM items")
    suspend fun clearAll()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY date DESC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY date DESC")
    suspend fun getAll(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BillEntity?

    @Query("SELECT * FROM bills WHERE no = :no LIMIT 1")
    suspend fun getByNo(no: String): BillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bills: List<BillEntity>)

    @Query("DELETE FROM bills")
    suspend fun clearAll()
}

@Dao
interface WSaleDao {
    @Query("SELECT * FROM wsales ORDER BY date DESC")
    fun observeAll(): Flow<List<WSaleEntity>>

    @Query("SELECT * FROM wsales WHERE date >= :fromMs ORDER BY date DESC")
    fun observeSince(fromMs: Long): Flow<List<WSaleEntity>>

    @Query("SELECT * FROM wsales ORDER BY date DESC")
    suspend fun getAll(): List<WSaleEntity>

    @Query("SELECT * FROM wsales WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WSaleEntity?

    @Query("SELECT * FROM wsales WHERE syncState = 'pending' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingSync(limit: Int = 100): List<WSaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sale: WSaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sales: List<WSaleEntity>)

    @Query("UPDATE wsales SET syncState = :state, syncAt = :at, syncErr = :err WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String, at: Long, err: String?)

    @Query("DELETE FROM wsales")
    suspend fun clearAll()
}

@Dao
interface StockMoveDao {
    @Query("SELECT * FROM stock_moves WHERE itemId = :itemId ORDER BY ts DESC")
    fun observeForItem(itemId: String): Flow<List<StockMoveEntity>>

    @Query("SELECT * FROM stock_moves ORDER BY ts ASC")
    suspend fun getAll(): List<StockMoveEntity>

    @Query("SELECT COALESCE(SUM(qty), 0) FROM stock_moves WHERE itemId = :itemId")
    suspend fun stockOf(itemId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM stock_moves WHERE id = :moveId)")
    suspend fun exists(moveId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(move: StockMoveEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(moves: List<StockMoveEntity>)

    @Query("DELETE FROM stock_moves")
    suspend fun clearAll()
}

@Dao
interface PendingQueueDao {
    @Query("SELECT * FROM pending_queue ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingQueueEntity>>

    @Query("SELECT * FROM pending_queue WHERE state = 'pending' AND nextAt <= :nowMs ORDER BY createdAt ASC")
    suspend fun getDue(nowMs: Long): List<PendingQueueEntity>

    @Query("SELECT * FROM pending_queue WHERE kind = :kind AND rid = :rid LIMIT 1")
    suspend fun findByKindAndRid(kind: String, rid: String): PendingQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: PendingQueueEntity)

    @Query("DELETE FROM pending_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pending_queue")
    suspend fun clearAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cat: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cats: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM categories")
    suspend fun clearAll()
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_log ORDER BY ts DESC LIMIT 200")
    fun observeRecent(): Flow<List<AuditEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: AuditEntity)

    @Query("DELETE FROM audit_log")
    suspend fun clearAll()
}

@Dao
interface SettingDao {
    @Query("SELECT valueJson FROM settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT valueJson FROM settings WHERE key = :key LIMIT 1")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}
