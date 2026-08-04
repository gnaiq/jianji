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
        QuickTemplate::class,
        Tag::class,
        TransactionTagCrossRef::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class JianjiDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun quickTemplateDao(): QuickTemplateDao
    abstract fun tagDao(): TagDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN toAccountId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE recurring_transactions ADD COLUMN monthOfYear INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE categories ADD COLUMN isSystem INTEGER NOT NULL DEFAULT 0")
                // 系统「转账」分类：供账户间转账交易引用，避免外键悬空；isSystem=1 不展示给用户
                // 关键修复：color 为 NOT NULL 列，必须显式赋值，否则升级用户（DB v3→v4）迁移时
                // 触发 NOT NULL constraint failed 导致启动即闪退（v1.6.0 回归根因）
                db.execSQL("INSERT INTO categories (name, icon, color, type, isDefault, sortOrder, parentId, isSystem) VALUES ('转账','🔄','#6200EE','EXPENSE',0,999,0,1)")
                // 兜底：回填历史可能缺失的 color（实体读取为 String 非空，避免潜在的 null 读取问题）
                db.execSQL("UPDATE categories SET color = '#6200EE' WHERE color IS NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // date 索引（§1 P0 查询下推配套）。索引名必须与 Room 生成的
                // index_transactions_date 完全一致，否则 schema 校验失败
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
                // 防御性补齐：实体自始声明 accountId/description 索引，但历史迁移链
                // （1→2→3→4）从未 CREATE INDEX。迁移后 Room 会校验全部索引，
                // 若老升级用户 DB 缺失即崩。IF NOT EXISTS 已存在则 no-op，缺失则修复。
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_description` ON `transactions` (`description`)")
            }
        }

        // v1.6.6：金额 Long 分 + 回收站软删 + 标签系统
        // 采用「建新表 -> 迁数据 -> 删旧表 -> 重命名」重建 transactions，
        // 确保迁移后 schema 与 Room 依据实体生成的 v6 定义逐列一致：
        //  - 规避 ALTER ADD COLUMN 在 NOT NULL/默认值/自动外键索引上的校验差异导致升级闪退；
        //  - 补齐 transactions.categoryId 的自动外键索引 index_transactions_categoryId
        //    （Room 为外键自动建索引，但历史迁移链从未 CREATE，老版本升级用户缺此索引会校验失败）。
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) 重建 transactions 表
                db.execSQL(
                    """
                    CREATE TABLE `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `amount_cents` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `date` TEXT NOT NULL,
                        `accountId` INTEGER,
                        `toAccountId` INTEGER,
                        `deleted_at` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """
                )
                // 迁移历史数据：金额 Double -> Long 分（四舍五入），软删标记置 NULL
                db.execSQL(
                    """
                    INSERT INTO `transactions_new` (
                        id, categoryId, amount_cents, type, description, date,
                        accountId, toAccountId, deleted_at, createdAt, updatedAt
                    ) SELECT
                        id, categoryId,
                        CAST(ROUND(COALESCE(amount, 0) * 100) AS INTEGER),
                        type, description, date,
                        accountId, toAccountId, NULL, createdAt, updatedAt
                    FROM `transactions`
                    """
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                // 重建全部索引（含 Room 为外键自动生成的 index_transactions_categoryId）
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_description` ON `transactions` (`description`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_deleted_at` ON `transactions` (`deleted_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")

                // 2) 标签系统
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        color TEXT NOT NULL DEFAULT '#6200EE',
                        icon TEXT NOT NULL DEFAULT '🏷️',
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_tags (
                        transactionId INTEGER NOT NULL,
                        tagId INTEGER NOT NULL,
                        PRIMARY KEY (transactionId, tagId),
                        FOREIGN KEY (transactionId) REFERENCES transactions(id) ON DELETE CASCADE,
                        FOREIGN KEY (tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tags_tagId` ON `transaction_tags` (`tagId`)")
            }
        }

        // v1.6.24：金额 Long 分存储统一（recurring_transactions + quick_templates）+ 清理 accounts.balance 死字段
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // recurring_transactions: amount REAL → amount_cents INTEGER
                db.execSQL("""
                    CREATE TABLE recurring_transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        amount_cents INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        accountId INTEGER,
                        frequency TEXT NOT NULL,
                        interval INTEGER NOT NULL DEFAULT 1,
                        dayOfMonth INTEGER NOT NULL DEFAULT 1,
                        dayOfWeek INTEGER NOT NULL DEFAULT 1,
                        nextRunDate TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        monthOfYear INTEGER NOT NULL DEFAULT 1,
                        createdAt TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO recurring_transactions_new SELECT
                        id, categoryId, CAST(ROUND(amount * 100) AS INTEGER),
                        type, description, accountId, frequency, interval,
                        dayOfMonth, dayOfWeek, nextRunDate, isActive, monthOfYear, createdAt
                    FROM recurring_transactions
                """)
                db.execSQL("DROP TABLE recurring_transactions")
                db.execSQL("ALTER TABLE recurring_transactions_new RENAME TO recurring_transactions")

                // quick_templates: amount REAL → amount_cents INTEGER
                db.execSQL("""
                    CREATE TABLE quick_templates_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        amount_cents INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        accountId INTEGER,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        useCount INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    INSERT INTO quick_templates_new SELECT
                        id, categoryId, CAST(ROUND(amount * 100) AS INTEGER),
                        type, description, accountId, sortOrder, useCount
                    FROM quick_templates
                """)
                db.execSQL("DROP TABLE quick_templates")
                db.execSQL("ALTER TABLE quick_templates_new RENAME TO quick_templates")

                // accounts: 移除 balance 列（余额已改为动态计算）
                db.execSQL("""
                    CREATE TABLE accounts_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL DEFAULT '💳',
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("INSERT INTO accounts_new SELECT id, name, icon, isDefault FROM accounts")
                db.execSQL("DROP TABLE accounts")
                db.execSQL("ALTER TABLE accounts_new RENAME TO accounts")
            }
        }

        // v1.6.27：修复 B1-4/B5-6 —— transactions.categoryId 外键 ON DELETE 由 CASCADE 改为 NO ACTION。
        // 重建 transactions 表以变更外键动作（SQLite 不支持 ALTER 外键），
        // 其余列与索引保持不变，数据无损迁移。
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `amount_cents` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `date` TEXT NOT NULL,
                        `accountId` INTEGER,
                        `toAccountId` INTEGER,
                        `deleted_at` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`)
                    )
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO `transactions_new` (
                        id, categoryId, amount_cents, type, description, date,
                        accountId, toAccountId, deleted_at, createdAt, updatedAt
                    ) SELECT
                        id, categoryId, amount_cents, type, description, date,
                        accountId, toAccountId, deleted_at, createdAt, updatedAt
                    FROM `transactions`
                    """
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_description` ON `transactions` (`description`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_deleted_at` ON `transactions` (`deleted_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
            }
        }

        // v1.6.30：budgets.amount REAL → amount_cents INTEGER（消除预算浮点误差）；
        // 先去重（保留 MAX(id)）再建唯一索引 (categoryId, year, month, period)；
        // 加 categoryId 外键 → categories.id ON DELETE NO ACTION（孤儿预算不再悬空，
        // 删分类时由应用层 reassign 后再删，见 CategoryRepository.deleteCategory）。
        // 重建表模式（SQLite 不支持 ALTER 改列类型/加唯一索引/加外键）。
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) 去重：把重复 (categoryId, year, month, period) 组中除 MAX(id) 外的行删除
                db.execSQL(
                    """
                    DELETE FROM budgets
                    WHERE id NOT IN (
                        SELECT MAX(id) FROM budgets
                        GROUP BY categoryId, year, month, period
                    )
                    """
                )
                // 2) 重建表（amount → amount_cents 整数分；舍入误差按 ROUND 处理存量 Double）
                // ⚠️ 外键子句必须与 @Entity(foreignKeys=) + Room 导出的 9.json 逐字一致，
                // 即必须显式写出 `ON UPDATE NO ACTION ON DELETE NO ACTION`（SQLite 省略默认等同，
                // 但 Room MigrationTestHelper 启动校验是逐字符比对，缺此即报 schema mismatch 闪退）。
                // v1.6.30 曾因此缺子句导致升级用户启动即崩，此行是血泪基线，勿删。
                db.execSQL(
                    """
                    CREATE TABLE `budgets_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `categoryId` INTEGER,
                        `amount_cents` INTEGER NOT NULL,
                        `period` TEXT NOT NULL DEFAULT 'MONTHLY',
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO `budgets_new` (
                        id, categoryId, amount_cents, period, year, month
                    ) SELECT
                        id, categoryId,
                        CAST(ROUND(COALESCE(amount, 0) * 100) AS INTEGER),
                        period, year, month
                    FROM `budgets`
                    """
                )
                db.execSQL("DROP TABLE `budgets`")
                db.execSQL("ALTER TABLE `budgets_new` RENAME TO `budgets`")
                // 3) 唯一索引（与 @Entity indices 完全一致，否则 schema 校验失败）
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_year_month_period` ON `budgets` (`categoryId`, `year`, `month`, `period`)")
                // 4) categoryId 单列索引（外键字段，Room 生成的 index_budgets_categoryId）
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
            }
        }

        fun getDatabase(context: Context): JianjiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JianjiDatabase::class.java,
                    "jianji_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    // 版本回退机制：允许 schema 降级时走破坏性迁移，避免回退到旧版
                    // （如从未来 v1.6.7 DB v7 回退到本版 v1.6.6 DB v6）时因 Room 拒绝降级而闪退。
                    // 代价是回退会清空 DB，属回退预期内的数据损失，已在 rollback 脚本中说明。
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}