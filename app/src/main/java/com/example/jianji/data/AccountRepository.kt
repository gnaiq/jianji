package com.example.jianji.data

import kotlinx.coroutines.flow.Flow

class AccountRepository(private val dao: AccountDao) {
    fun observeAll(): Flow<List<Account>> = dao.observeAll()
    suspend fun getAll(): List<Account> = dao.getAll()
    suspend fun getById(id: Long): Account? = dao.getById(id)
    suspend fun getDefault(): Account? = dao.getDefault()
    suspend fun insert(account: Account): Long = dao.insert(account)
    suspend fun update(account: Account) = dao.update(account)
    suspend fun delete(account: Account) = dao.delete(account)
    suspend fun setDefault(id: Long) {
        dao.clearDefaults()
        dao.setDefault(id)
    }

    suspend fun seedDefaults() {
        if (dao.getAll().isEmpty()) {
            dao.insert(Account(name = "现金", icon = "💵", isDefault = true))
            dao.insert(Account(name = "微信", icon = "💬"))
            dao.insert(Account(name = "支付宝", icon = "🔵"))
            dao.insert(Account(name = "银行卡", icon = "🏦"))
        }
    }
}
