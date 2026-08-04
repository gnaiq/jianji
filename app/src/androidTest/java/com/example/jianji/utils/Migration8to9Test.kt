package com.example.jianji.utils

import android.content.ContentValues
import androidx.room.Room
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
 * 用 MigrationTestHelper 走完整迁移链并校验目标 schema（依赖 app/schemas/9.json 基线，
 * 该基线已由 v1.6.30 CI 生成并提交入库）。
 *
 * 断言：① 不抛 IllegalStateException（schema 校验通过，不闪退）；
 *       ② 金额精度：12.34 → 1234 分；
 *       ③ 去重：3 条重复 (categoryId,year,month,period) 仅保留 MAX(id) 一条；
 *       ④ 唯一索引建立；⑤ categoryId 外键为 NO ACTION（删分类不级联删预算）。
 *
 * ⚠️ 本测试是 v1.6.30「迁移外键缺失导致升级闪退」的回归防护网：
 *    runMigrationsAndValidate(..., validateDroppedTables=true) 会逐字比对迁移后结构
 *    与 9.json，任何外键/列/索引不一致都会在此暴露。禁止再次 @Ignore。
 */
class Migration8to9Test {
    private val TEST_DB = "mig_test_8to9"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JianjiDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun `v8升级到v9_预算转分且去重且外键NO_ACTION`() {
        // 1) createDatabase(TEST_DB, 8) 会依据 app/schemas/8.json 自动建好 v8 全部表，
        //    这里只插入数据，不要把 DDL 手搓一遍（手搓会与 MigrationTestHelper 校验打架，
        //    导致 "budgets already exists" / "categories.icon NOT NULL" 等假失败）。
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL("INSERT INTO categories (id,name,type) VALUES (1,'餐饮','EXPENSE')")
            // 构造 3 条重复预算（categoryId=1,year=2026,month=8,MONTHLY），金额不同
            execSQL("INSERT INTO budgets (id,categoryId,amount,period,year,month) VALUES (10,1,12.34,'MONTHLY',2026,8)")
            execSQL("INSERT INTO budgets (id,categoryId,amount,period,year,month) VALUES (11,1,99.99,'MONTHLY',2026,8)")
            execSQL("INSERT INTO budgets (id,categoryId,amount,period,year,month) VALUES (12,1,55.55,'MONTHLY',2026,8)")
            // 一条总预算（categoryId=NULL）
            execSQL("INSERT INTO budgets (id,categoryId,amount,period,year,month) VALUES (20,NULL,1000.0,'MONTHLY',2026,8)")
            close()
        }

        // 2) 触发完整迁移链至 v9（依赖 app/schemas/9.json 校验目标 schema，逐字比对外键/索引）
        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, JianjiDatabase.MIGRATION_8_9)

        // 3) 断言金额精度：保留的 MAX(id)=12 那条应为 5555 分
        db.query("SELECT amount_cents FROM budgets WHERE id=12").use {
            assertTrue(it.moveToFirst())
            assertEquals(5555L, it.getLong(0))
        }
        // 4) 断言去重：仅剩 1 条 categoryId=1 的重复组（MAX(id)=12）
        db.query("SELECT COUNT(*) FROM budgets WHERE categoryId=1 AND year=2026 AND month=8").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        // 5) 唯一索引存在
        db.query("PRAGMA index_list('budgets')").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                if (name == "index_budgets_categoryId_year_month_period" && unique) found = true
            }
            assertTrue("唯一索引 index_budgets_categoryId_year_month_period 应存在", found)
        }
        // 6) 外键为 NO ACTION：删分类后预算仍在
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
        // 仅建库（依据 8.json 自动建表），不插任何 budgets 数据
        helper.createDatabase(TEST_DB + "_empty", 8).apply {
            execSQL("INSERT INTO categories (id,name,type) VALUES (1,'餐饮','EXPENSE')")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB + "_empty", 9, true, JianjiDatabase.MIGRATION_8_9)
        db.query("SELECT COUNT(*) FROM budgets").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }
}
