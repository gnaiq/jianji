package com.example.jianji.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Transaction::class,
        Category::class,
        Account::class,
        Budget::class,
        RecurringTransaction::class,
        QuickTemplate::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JianjiDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun quickTemplateDao(): QuickTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: JianjiDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新增表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL DEFAULT '💳',
                        balance REAL NOT NULL DEFAULT 0.0,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS budgets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER,
                        amount REAL NOT NULL,
                        period TEXT NOT NULL DEFAULT 'MONTHLY',
                        year INTEGER NOT NULL,
                        month INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recurring_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        accountId INTEGER,
                        frequency TEXT NOT NULL,
                        interval INTEGER NOT NULL DEFAULT 1,
                        dayOfMonth INTEGER NOT NULL DEFAULT 1,
                        dayOfWeek INTEGER NOT NULL DEFAULT 1,
                        nextRunDate TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS quick_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        accountId INTEGER,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        useCount INTEGER NOT NULL DEFAULT 0
                    )
                """)
                // 给旧表加列
                db.execSQL("ALTER TABLE transactions ADD COLUMN accountId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE categories ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): JianjiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JianjiDatabase::class.java,
                    "jianji_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
