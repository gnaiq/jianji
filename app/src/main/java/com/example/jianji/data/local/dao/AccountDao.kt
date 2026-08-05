package com.example.jianji.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
com.example.jianji.data.local.entity.*

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, id ASC")
    suspend fun getAll(): List<Account>

    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, id ASC")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert
    suspend fun insertAll(accounts: List<Account>): List<Long>

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("UPDATE accounts SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    // 原子化设置默认账户：避免两步操作间的竞态条件导致无默认账户
    @androidx.room.Transaction
    suspend fun setDefaultAtomic(id: Long) {
        clearDefaults()
        setDefault(id)
    }

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): Account?

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
