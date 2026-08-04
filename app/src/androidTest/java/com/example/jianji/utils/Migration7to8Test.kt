package com.example.jianji.utils

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jianji.data.JianjiDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 验收：v1.6.26(DB v7) → v1.6.27(DB v8) 升级路径零异常，且数据保留。
 * 直接构造 v7 结构库（含旧 CASCADE 外键的交易表 + 一条交易），触发 MIGRATION_7_8，
 * 断言：① 不抛 IllegalStateException（迁移校验通过，不闪退）；② 交易数据仍在；
 * ③ 外键已变为 NO ACTION（删除分类不再级联删交易，B1-4/B5-6 修复生效）。
 */
@RunWith(AndroidJUnit4::class)
class Migration7to8Test {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dbFile = ctx.getDatabasePath("mig_test_7to8")
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) dbFile.delete()
    }

    @Test
    fun `v7 升级到 v8 不闪退 且交易保留 且外键为 NO ACTION`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // 1) 手工建一个 v7 结构的库（版本号 7，外键 ON DELETE CASCADE 模拟旧版）
        val v7 = Room.databaseBuilder(ctx, JianjiDatabase::class.java, "mig_test_7to8")
            .setVersion(7) // 直接声明为 v7，避免再跑 1..6 迁移
            .build()
        v7.openHelper.writableDatabase.apply {
            execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, icon TEXT NOT NULL DEFAULT '💰', color TEXT NOT NULL DEFAULT '#6200EE', type TEXT NOT NULL, isDefault INTEGER NOT NULL DEFAULT 0, isSystem INTEGER NOT NULL DEFAULT 0, sortOrder INTEGER NOT NULL DEFAULT 0, parentId INTEGER NOT NULL DEFAULT 0)")
            execSQL("INSERT INTO categories (id,name,type) VALUES (1,'餐饮','EXPENSE')")
            execSQL("""CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                categoryId INTEGER NOT NULL,
                amount_cents INTEGER NOT NULL,
                type TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                date TEXT NOT NULL,
                accountId INTEGER,
                toAccountId INTEGER,
                deleted_at TEXT,
                createdAt TEXT NOT NULL,
                updatedAt TEXT NOT NULL,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
            )""")
            execSQL("INSERT INTO transactions (categoryId,amount_cents,type,date,createdAt,updatedAt) VALUES (1,1234,'EXPENSE','2026-08-04T00:00:00','2026-08-04T00:00:00','2026-08-04T00:00:00')")
        }
        v7.close()

        // 2) 用 MIGRATION_7_8 打开（v7→v8），不应抛 Migration didn't properly handle
        val upgraded = Room.databaseBuilder(ctx, JianjiDatabase::class.java, "mig_test_7to8")
            .addMigrations(JianjiDatabase.MIGRATION_7_8)
            .build()
        val rows = upgraded.transactionDao().getAllSnapshot()
        assertEquals("升级后交易数据应保留", 1, rows.size)
        assertEquals(1234L, rows.first().amountCents)

        // 3) 外键已变 NO ACTION：删分类后交易仍在（验证迁移后的 DDL 不再是 CASCADE）
        upgraded.categoryDao().delete(
            com.example.jianji.data.Category(id = 1, name = "餐饮", type = com.example.jianji.data.CategoryType.EXPENSE)
        )
        val after = upgraded.transactionDao().getAllSnapshot()
        assertEquals("外键应为 NO ACTION，删分类不再级联删交易", 1, after.size)
        upgraded.close()
    }
}
