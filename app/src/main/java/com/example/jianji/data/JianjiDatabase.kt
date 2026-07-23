package com.example.jianji.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Transaction::class, Category::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JianjiDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: JianjiDatabase? = null

        fun getDatabase(context: Context): JianjiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JianjiDatabase::class.java,
                    "jianji_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Initialize default categories
            val database = INSTANCE ?: return
            CoroutineScope(Dispatchers.IO).launch {
                val categoryDao = database.categoryDao()

                val defaultCategories = listOf(
                    // Income categories
                    Category(name = "工资", icon = "💼", type = CategoryType.INCOME, isDefault = true),
                    Category(name = "奖金", icon = "🎁", type = CategoryType.INCOME, isDefault = true),
                    Category(name = "投资收益", icon = "📈", type = CategoryType.INCOME, isDefault = true),
                    Category(name = "其他收入", icon = "💰", type = CategoryType.INCOME, isDefault = true),
                    // Expense categories
                    Category(name = "食物", icon = "🍔", type = CategoryType.EXPENSE, isDefault = true),
                    Category(name = "交通", icon = "🚗", type = CategoryType.EXPENSE, isDefault = true),
                    Category(name = "娱乐", icon = "🎮", type = CategoryType.EXPENSE, isDefault = true),
                    Category(name = "购物", icon = "🛍️", type = CategoryType.EXPENSE, isDefault = true),
                    Category(name = "医疗", icon = "🏥", type = CategoryType.EXPENSE, isDefault = true),
                    Category(name = "教育", icon = "📚", type = CategoryType.EXPENSE, isDefault = true),
                    Category(name = "其他支出", icon = "💸", type = CategoryType.EXPENSE, isDefault = true)
                )

                for (category in defaultCategories) {
                    categoryDao.insert(category)
                }
            }
        }
    }
}
