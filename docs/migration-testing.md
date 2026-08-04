# Room 数据库迁移测试指南

本文档说明简记（jianji）项目的 Room 数据库 schema 导出机制、迁移测试设施，以及编写迁移测试的流程与模板。

## 背景

`JianjiDatabase` 当前版本为 **6**。自本次整改起（项7），已开启 schema 导出：

- `JianjiDatabase.kt`：`@Database(..., exportSchema = true)`
- `app/build.gradle.kts`：
  ```kotlin
  ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
  }
  ```
  Room 编译期会把每个数据库版本的结构导出为 `app/schemas/com.example.jianji.data.JianjiDatabase/<version>.json`。
- `androidTest` 的 assets 已挂载该目录，供 `MigrationTestHelper` 在测试运行时读取：
  ```kotlin
  sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
  ```
- 测试依赖：`androidTestImplementation("androidx.room:room-testing:2.6.1")`（内含 `MigrationTestHelper`）。

## 已知约束（重要）

### 1. 历史版本（1~5）无 schema JSON，无法回溯测试全链

`exportSchema` 在本次整改前一直是 `false`，因此版本 **1、2、3、4、5** 从未导出过 schema JSON。

`MigrationTestHelper` 依赖「起始版本的 schema JSON」来创建旧库、再套用迁移。缺少 1~5 的 JSON 意味着：

- **无法**编写 `1→6`、`2→6` 等跨历史版本的自动化迁移测试；
- 历史迁移（`MIGRATION_1_2` … `MIGRATION_5_6`）的正确性只能依靠：既有线上/真机升级未出现问题这一经验事实，以及人工代码走读；
- 不要试图手工补写历史 JSON —— 手写极易与当时真实 schema 不一致，反而制造「测试通过但与线上不符」的假象。

### 2. 本地禁止构建，v6 schema JSON 由 CI 生成

项目铁律：**禁止本地 gradle 构建**，编译/产物一律以 GitHub Actions CI 为准。因此 `app/schemas/.../6.json` 不会在本地出现，须由下一次 CI 构建生成。

## CI 生成 schema 后的入库流程

`schemas/` 目录**必须提交进版本库**（它是迁移测试的基线，不是构建缓存）。首次生成后按如下流程入库：

1. 触发一次 CI 构建（按项目 GitHub API SOP 推送 commit + annotated tag 触发 `build-apk` workflow）。
2. 构建成功后，从 CI 产物 / 工作副本中取得新生成的
   `app/schemas/com.example.jianji.data.JianjiDatabase/6.json`。
   - 若 workflow 未上传 `schemas/` 为 artifact，需在 `build-apk.yml` 增加一步
     `actions/upload-artifact`，把 `app/schemas/**` 上传后下载取回。
3. 将 `6.json`（以及后续每个新版本的 JSON）**提交入库**，路径保持
   `app/schemas/com.example.jianji.data.JianjiDatabase/<version>.json`。
4. 自此，`6→7` 及以后的迁移即可用 `MigrationTestHelper` 编写自动化测试。

> **状态更新（2026-08-04，v1.6.29）**：已提交**真实的** `app/schemas/com.example.jianji.data.JianjiDatabase/8.json`
> （由 CI `jianji-room-schemas` artifact 取回，Room 编译期真实导出）。该基线可用于未来 v8→v9 迁移测试。
> **关于 7.json**：项目历史上从未导出过 v7 的 schema JSON（v7→v8 跳跃式提交，CI 仅导出当前版 v8），
> 且 Room `identityHash` 无法手工可靠重算，故**不伪造 7.json**。v7→v8 升级路径改由
> `Migration7to8Test`（androidTest，手工建 v7 库触发 `MIGRATION_7_8`，断言不闪退+数据保留+外键变 NO ACTION）
> 在 CI `connectedCheck` 覆盖，已在 v1.6.28/v1.6.29 多次通过。后续若需 `MigrationTestHelper` 标准基线测试，
> 可在一次 v7 临时构建中取回 7.json 再补。
> CI 已新增 `upload-artifact: jianji-room-schemas` 步骤，后续每次构建自动产出最新 schema 供取回入库。

> 说明：把 `schemas/` 纳入 `.gitignore` 是**错误**做法 —— 一旦忽略，迁移测试将失去基线。

## 未来 6→7 迁移测试模板

当 schema 升级到版本 7 并且 `6.json`、`7.json` 均已入库后，可用下面的模板编写迁移测试。

放置位置：`app/src/androidTest/java/com/example/jianji/data/MigrationTest.kt`

```kotlin
package com.example.jianji.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JianjiDatabase::class.java,
        emptyList(),                       // AutoMigrationSpec 列表，无则留空
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * 6 -> 7 迁移测试。
     * 前置条件：app/schemas/ 下已存在 6.json 与 7.json（由 CI 构建导出并入库）。
     */
    @Test
    @Throws(IOException::class)
    fun migrate6To7() {
        // 1) 用版本 6 的 schema 建库，写入代表性旧数据
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO transactions (id, amountCents, type, categoryId, date, description) " +
                    "VALUES (1, 1000, 'EXPENSE', 1, '2026-01-01', '迁移前样本')"
            )
            close()
        }

        // 2) 套用真实迁移（MIGRATION_6_7 定义在 JianjiDatabase 中），validateDroppedTables=true 校验结构
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 7, true, MIGRATION_6_7
        )

        // 3) 断言旧数据仍在、新列/新表符合预期
        db.query("SELECT description FROM transactions WHERE id = 1").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("迁移前样本", c.getString(0))
        }
        db.close()
    }
}
```

### 编写要点

- `MIGRATION_6_7` 请引用 `JianjiDatabase.kt` 中定义的真实 `Migration` 对象，**不要**在测试里另写一份。
- `runMigrationsAndValidate` 的第 3 个参数 `validateDroppedTables = true` 会用 `7.json` 校验迁移后结构，是发现「迁移漏改列」的关键。
- 每新增一个版本 N：CI 会生成 `N.json`，入库后即可补一条 `(N-1)→N` 测试，逐步补齐迁移测试链。
- 破坏性迁移（`fallbackToDestructiveMigration`）不在此测试覆盖范围内 —— 那会丢数据，需在业务层单独评估。

## 检查清单（每次 schema 升级时）

- [ ] `JianjiDatabase` version 号已 +1
- [ ] 已定义对应的 `MIGRATION_(N-1)_N` 并注册到 `Room.databaseBuilder(...).addMigrations(...)`
- [ ] CI 构建生成了新的 `<N>.json` 并已提交入库
- [ ] 已补充 `(N-1)→N` 的 `MigrationTest`
- [ ] CI 上 `connectedAndroidTest`（或对应任务）通过
