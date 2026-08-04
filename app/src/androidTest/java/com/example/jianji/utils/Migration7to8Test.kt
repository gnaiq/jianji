package com.example.jianji.utils

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jianji.data.JianjiDatabase
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * 验收：v1.6.26(DB v7) → v1.6.27(DB v8) 升级路径零异常，且数据保留、外键变 NO ACTION。
 *
 * ⚠️ 暂以 @Ignore 关闭：本测试运行依赖 app/schemas/7.json 与 8.json 基线，
 * 而 7.json 历史从未导出（见 docs/migration-testing.md）。基线需由 CI 编译期 Room 导出
 * （build-apk.yml 上传 jianji-room-schemas artifact），取回提交仓库后再取消 @Ignore 启用。
 * 当前 v7→v8 的 FK NO ACTION 修复已随 v1.6.28 发布，且 v8→v9 由 Migration8to9Test 覆盖。
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(/* 启用时填入 Migration7to8RealTest::class */)
class Migration7to8Test

@Ignore("待 schemas/7.json + 8.json 基线提交后启用（见类注释）")
class Migration7to8RealTest {
    private val TEST_DB = "mig_test_7to8"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JianjiDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `v7升级到v8_不闪退且交易保留且外键为NO_ACTION`() {
        helper.createDatabase(TEST_DB, 7).apply {
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
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true,
            JianjiDatabase.MIGRATION_7_8, JianjiDatabase.MIGRATION_8_9)
        db.query("SELECT amount_cents FROM transactions").use {
            it.moveToFirst(); assertEquals(1234L, it.getLong(0))
        }
        db.close()
    }
}
