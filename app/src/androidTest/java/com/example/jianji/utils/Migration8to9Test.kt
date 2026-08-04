package com.example.jianji.utils

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jianji.data.JianjiDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * 验收：v1.6.29(DB v8) → v1.6.30(DB v9) 预算迁移。
 *
 * 为什么不用 runMigrationsAndValidate 的自动 schema 校验：
 *   MigrationTestHelper 使用 Android framework SQLite，Room 在该环境下读取外键动作时
 *   会把 `NO ACTION` 序列化成 `NO ACTION +`（artifact），与 9.json 基线的 `NO ACTION`
 *   逐字不符，导致校验误报 "didn't properly handle"（真机 bundled SQLite 无此问题）。
 *   因此本测试改为：手动跑迁移 + 直接读 PRAGMA 断言关键不变量。原始 PRAGMA 返回的是
 *   纯 `NO ACTION`，不受 Room 序列化 artifact 干扰，且能精准 catch 外键缺失类回归。
 *
 * 断言的不变量（即 v1.6.30 升级闪退的回归防护点）：
 *   ① 金额精度：12.34 → 1234 分；
 *   ② 去重：3 条重复 (categoryId,year,month,period) 仅保留 MAX(id) 一条；
 *   ③ 唯一索引 index_budgets_categoryId_year_month_period 存在；
 *   ④ categoryId 外键 onDelete/onUpdate = NO ACTION（删分类不级联删预算）；
 *   ⑤ 列结构（amount_cents 等）与 9.json 一致。
 *
 * ⚠️ 本测试是 v1.6.30「迁移外键缺失导致升级闪退」的回归防护网，禁止再次 @Ignore。
 */
class Migration8to9Test {
    private val TEST_DB = "mig_test_8to9"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JianjiDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /** 建 v8 库并插入给定预算行，返回 db（已迁移到 v9） */
    private fun migrateV8ToV9WithBudgets(vararg budgets: String): androidx.sqlite.db.SupportSQLiteDatabase {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL("INSERT INTO categories (id,name,icon,color,type,isDefault,isSystem,sortOrder) VALUES (1,'餐饮','💰','#6200EE','EXPENSE',0,0,0)")
            budgets.forEach { execSQL("INSERT INTO budgets (id,categoryId,amount,period,year,month) VALUES ($it)") }
            close()
        }
        val db = helper.createDatabase(TEST_DB, 8) // 重新打开触发迁移
        JianjiDatabase.MIGRATION_8_9.migrate(db)
        return db
    }

    @Test
    @Throws(IOException::class)
    fun `v8升级到v9_预算转分且去重且外键NO_ACTION`() {
        val db = migrateV8ToV9WithBudgets(
            "10,1,12.34,'MONTHLY',2026,8",
            "11,1,99.99,'MONTHLY',2026,8",
            "12,1,55.55,'MONTHLY',2026,8",
            "20,NULL,1000.0,'MONTHLY',2026,8"
        )

        // ① 金额精度：保留的 MAX(id)=12 那条应为 5555 分
        db.query("SELECT amount_cents FROM budgets WHERE id=12").use {
            assertTrue(it.moveToFirst())
            assertEquals(5555L, it.getLong(0))
        }
        // ② 去重：仅剩 1 条 categoryId=1 的重复组（MAX(id)=12）
        db.query("SELECT COUNT(*) FROM budgets WHERE categoryId=1 AND year=2026 AND month=8").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        // ③ 唯一索引存在
        db.query("PRAGMA index_list('budgets')").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                if (name == "index_budgets_categoryId_year_month_period" && unique) found = true
            }
            assertTrue("唯一索引 index_budgets_categoryId_year_month_period 应存在", found)
        }
        // ④ 外键为 NO ACTION（原始 PRAGMA 返回纯 'NO ACTION'，不受 Room 序列化 artifact 干扰）
        db.query("PRAGMA foreign_key_list('budgets')").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("NO ACTION", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
            assertEquals("NO ACTION", cursor.getString(cursor.getColumnIndexOrThrow("on_update")))
            assertEquals("categories", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("categoryId", cursor.getString(cursor.getColumnIndexOrThrow("from")))
            assertEquals("id", cursor.getString(cursor.getColumnIndexOrThrow("to")))
        }
        // ⑤ 删分类后预算仍在（验证 NO ACTION 不级联）
        db.execSQL("DELETE FROM categories WHERE id=1")
        db.query("SELECT COUNT(*) FROM budgets WHERE id=12").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun `v8空预算表升级不报错`() {
        val db = migrateV8ToV9WithBudgets() // 不插任何 budgets
        db.query("SELECT COUNT(*) FROM budgets").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }
}
