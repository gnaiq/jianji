package com.example.jianji.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("UPDATE accounts SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): Account?
}
