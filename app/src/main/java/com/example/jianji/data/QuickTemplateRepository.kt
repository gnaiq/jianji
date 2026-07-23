package com.example.jianji.data

class QuickTemplateRepository(private val dao: QuickTemplateDao) {
    suspend fun getAll(): List<QuickTemplate> = dao.getAll()
    suspend fun getByType(type: TransactionType): List<QuickTemplate> = dao.getByType(type)
    suspend fun getById(id: Long): QuickTemplate? = dao.getById(id)
    suspend fun insert(template: QuickTemplate): Long = dao.insert(template)
    suspend fun update(template: QuickTemplate) = dao.update(template)
    suspend fun delete(template: QuickTemplate) = dao.delete(template)
    suspend fun incrementUseCount(id: Long) = dao.incrementUseCount(id)
    suspend fun deleteAll() = dao.deleteAll()
}
