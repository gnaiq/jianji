# 阶段四 · 规划项设计文档（PHASE4_PLAN）

> 本文档为**只读设计产出**，不修改任何生产代码/构建配置。执行前提：
> ① 金额 v7 迁移需改动 `JianjiDatabase.kt` / `SettingsScreen.kt` 等文件，正被阶段三并行重构，须在其重构落地后再动手，避免冲突。
> ② 迁移测试须**复用第 7 项建好的迁移测试设施**——经实测，`app/src/androidTest/` 目录当前**尚不存在**，`app/src/test/` 仅有 3 个纯单元测试（`RecurringScheduleTest` / `StatisticsCalculatorTest` / `VersionComparatorTest`），**无 Room MigrationTest**。故本项迁移测试**强依赖第 7 项先落地**。
> ③ 包名更换**仅在决定上架时执行**。
>
> 当前基线（实测）：`JianjiDatabase.version = 6`；`Transaction` 已在 `MIGRATION_5_6` 迁至 Long 分（`@ColumnInfo(name = "amount_cents") val amountCents: Long`）。本项即对齐该范式，把剩余 4 个 Double 金额字段迁到 v7。

---

## 1. 金额精度统一迁移设计（Double → Long 分）

### 1.1 现状 schema 实测（逐列核对）

| 实体 | 表名 | 目标字段 | 当前类型 | 当前列名 | 建表来源 |
|------|------|----------|----------|----------|----------|
| `Budget` | `budgets` | `amount` | `Double` | `amount`(REAL NOT NULL) | MIGRATION_1_2:55 |
| `Account` | `accounts` | `balance` | `Double`=0.0 | `balance`(REAL NOT NULL DEFAULT 0.0) | MIGRATION_1_2:47 |
| `RecurringTransaction` | `recurring_transactions` | `amount` | `Double` | `amount`(REAL NOT NULL) | MIGRATION_1_2:65 |
| `QuickTemplate` | `quick_templates` | `amount` | `Double` | `amount`(REAL NOT NULL) | MIGRATION_1_2:83 |

对齐参照：`Transaction.amountCents: Long` + `@ColumnInfo(name="amount_cents")`（Transaction.kt:35-36）。

### 1.2 全部 Double 金额字段调用点清单（文件:行号，实测）

> 报告估计约 15 处，**实测有效改动点 = 23 处**（含 4 处实体声明 + 6 处 import DTO/映射 + 13 处业务读写）。下表已剔除 `Transaction.amountCents` 干扰项及局部计算变量（HomeScreen:182/185、TransactionViewModel:67-86 的 `balance`/`accountBalances` 是由交易汇总的**运行时计算值**，与 `Account.balance` 持久化列无关，**不迁移**）。

#### A. budget.amount（6 处）
| # | 文件:行 | 用途 | 改法 |
|---|---------|------|------|
| 1 | `data/Budget.kt:11` | 实体声明 `val amount: Double` | → `@ColumnInfo(name="amount_cents") val amountCents: Long` |
| 2 | `ui/viewmodel/BudgetViewModel.kt:18-20` | `getMonthlyBudget(): Flow<Double>` 映射 `it?.amount ?: 0.0` | → `.map { (it?.amountCents ?: 0L) / 100.0 }`（保持对 UI 暴露元为单位） |
| 3 | `ui/screens/SettingsScreen.kt:716` | `val amt = budgetAmount.toDoubleOrNull()` | 保留输入解析为 Double |
| 4 | `ui/screens/SettingsScreen.kt:718-719` | `Budget(amount = amt, ...)` | → `Budget(amountCents = Math.round(amt * 100), ...)` |
| 5 | `utils/DataImportManager.kt:49` | `BudgetImport.amount: Double`（JSON DTO，见 §1.5） | DTO 保留元 Double，映射时转分 |
| 6 | `utils/DataImportManager.kt:144 / 223` | 导出 `amount = b.amount`、导入 `Budget(amount = b.amount,...)` | 导出 `amount = b.amountCents/100.0`；导入 `amountCents = Math.round(b.amount*100)` |

#### B. account.balance（3 处持久化 + 1 处展示）
| # | 文件:行 | 用途 | 改法 |
|---|---------|------|------|
| 1 | `data/Account.kt:12` | 实体声明 `val balance: Double = 0.0` | → `@ColumnInfo(name="balance_cents") val balanceCents: Long = 0` |
| 2 | `utils/DataImportManager.kt:42` | `AccountImport.balance: Double` DTO | DTO 保留元 Double |
| 3 | `utils/DataImportManager.kt:140 / 217` | 导出 `balance = a.balance`、导入 `Account(balance = a.balance,...)` | 导出 `balance = a.balanceCents/100.0`；导入 `balanceCents = Math.round(a.balance*100)` |
| — | `ui/screens/SettingsScreen.kt:760` | 展示用 `accountBalances[acc.id]`（**运行时汇总值**，非本列） | **无需改** |

> 说明：`Account.balance` 列目前在 UI 中**无直接写入点**（余额由交易汇总实时算出，见 `TransactionViewModel.accountBalances`）。持久化列仅在备份/恢复往返中传递，故迁移点集中在实体声明 + import 层。

#### C. recurring.amount（6 处）
| # | 文件:行 | 用途 | 改法 |
|---|---------|------|------|
| 1 | `data/RecurringTransaction.kt:12` | 实体声明 `val amount: Double` | → `@ColumnInfo(name="amount_cents") val amountCents: Long` |
| 2 | `ui/viewmodel/TransactionViewModel.kt:163` | `amountCents = Math.round(rtx.amount * 100)`（生成交易时） | → `amountCents = rtx.amountCents`（两侧同为分，直接赋值） |
| 3 | `ui/screens/SettingsScreen.kt:1065` | `val amt = rAmount.toDoubleOrNull()` | 保留输入解析 |
| 4 | `ui/screens/SettingsScreen.kt:1072` | `RecurringTransaction(amount = amt, ...)` | → `amountCents = Math.round(amt * 100)` |
| 5 | `utils/DataImportManager.kt:59` | `RecurringImport.amount: Double` DTO | DTO 保留元 Double |
| 6 | `utils/DataImportManager.kt:150 / 239` | 导出 `amount = r.amount`、导入 `RecurringTransaction(amount = r.amount,...)` | 导出 `amount = r.amountCents/100.0`；导入 `amountCents = Math.round(r.amount*100)` |

#### D. template.amount（8 处）
| # | 文件:行 | 用途 | 改法 |
|---|---------|------|------|
| 1 | `data/QuickTemplate.kt:12` | 实体声明 `val amount: Double` | → `@ColumnInfo(name="amount_cents") val amountCents: Long` |
| 2 | `ui/JianjiApp.kt:206` | 模板套用 `amount = template.amount`（传给 AddTransactionDialog 的 Double 参数） | → `amount = template.amountCents / 100.0` |
| 3 | `ui/screens/HomeScreen.kt:340` | 展示 `"¥${template.amount.toInt()}"` | → `"¥${(template.amountCents/100.0).toInt()}"` |
| 4 | `ui/components/AddTransactionDialog.kt:174` | `amount = template.amount.toString()` | → `amount = (template.amountCents/100.0).toString()` |
| 5 | `ui/components/AddTransactionDialog.kt:197` | 展示 `"¥${template.amount.toInt()}"` | → `"¥${(template.amountCents/100.0).toInt()}"` |
| 6 | `ui/screens/SettingsScreen.kt:905` | `val amt = tmpAmount.toDoubleOrNull()` | 保留输入解析 |
| 7 | `ui/screens/SettingsScreen.kt:907` | `QuickTemplate(amount = amt, ...)` | → `amountCents = Math.round(amt * 100)` |
| 8 | `utils/DataImportManager.kt:76` + `160 / 263` | `TemplateImport.amount` DTO + 导出/导入映射 | 同上：DTO 元 Double，映射转分 |

**合计有效改动点 = 23 处**（budget 6 + account 4 + recurring 6 + template 8，其中 account:760 为无需改的展示点故记 3 持久化 +1 标注）。

### 1.3 MIGRATION_6_7 完整 SQL 草案

严格套用 `MIGRATION_5_6` 既有风格：**建新表 → ROUND 迁数据 → 删旧表 → 重命名 → 重建索引**。这 4 张表**均无自建索引**（实测 4 个实体 `@Entity` 无 `indices`，`budgets`/`accounts`/`recurring_transactions`/`quick_templates` 也无外键自动索引），故重命名后**无需 CREATE INDEX**。

```kotlin
// v1.6.7：金额 Long 分统一——budgets / accounts / recurring_transactions / quick_templates
// 四表金额列 Double(REAL) -> Long(INTEGER 分)，与 Transaction(v6) 范式对齐。
// 沿用 MIGRATION_5_6「建新表 -> ROUND 迁数据 -> 删旧表 -> 重命名」，
// 确保迁移后 schema 与 Room 依据 v7 实体生成的定义逐列一致。
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---- 1) budgets: amount(REAL) -> amount_cents(INTEGER) ----
        db.execSQL("""
            CREATE TABLE `budgets_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryId` INTEGER,
                `amount_cents` INTEGER NOT NULL,
                `period` TEXT NOT NULL,
                `year` INTEGER NOT NULL,
                `month` INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            INSERT INTO `budgets_new` (id, categoryId, amount_cents, period, year, month)
            SELECT id, categoryId,
                   CAST(ROUND(COALESCE(amount, 0) * 100) AS INTEGER),
                   period, year, month
            FROM `budgets`
        """)
        db.execSQL("DROP TABLE `budgets`")
        db.execSQL("ALTER TABLE `budgets_new` RENAME TO `budgets`")

        // ---- 2) accounts: balance(REAL) -> balance_cents(INTEGER) ----
        db.execSQL("""
            CREATE TABLE `accounts_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `icon` TEXT NOT NULL DEFAULT '💳',
                `balance_cents` INTEGER NOT NULL DEFAULT 0,
                `isDefault` INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            INSERT INTO `accounts_new` (id, name, icon, balance_cents, isDefault)
            SELECT id, name, icon,
                   CAST(ROUND(COALESCE(balance, 0) * 100) AS INTEGER),
                   isDefault
            FROM `accounts`
        """)
        db.execSQL("DROP TABLE `accounts`")
        db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")

        // ---- 3) recurring_transactions: amount(REAL) -> amount_cents(INTEGER) ----
        // 注意：逐列对齐 v7 实体（含 monthOfYear——MIGRATION_3_4 后已存在）
        db.execSQL("""
            CREATE TABLE `recurring_transactions_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `amount_cents` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `accountId` INTEGER,
                `frequency` TEXT NOT NULL,
                `interval` INTEGER NOT NULL DEFAULT 1,
                `dayOfMonth` INTEGER NOT NULL DEFAULT 1,
                `monthOfYear` INTEGER NOT NULL DEFAULT 1,
                `dayOfWeek` INTEGER NOT NULL DEFAULT 1,
                `nextRunDate` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL DEFAULT 1,
                `createdAt` TEXT NOT NULL
            )
        """)
        db.execSQL("""
            INSERT INTO `recurring_transactions_new` (
                id, categoryId, amount_cents, type, description, accountId,
                frequency, interval, dayOfMonth, monthOfYear, dayOfWeek,
                nextRunDate, isActive, createdAt
            ) SELECT
                id, categoryId,
                CAST(ROUND(COALESCE(amount, 0) * 100) AS INTEGER),
                type, description, accountId,
                frequency, interval, dayOfMonth, monthOfYear, dayOfWeek,
                nextRunDate, isActive, createdAt
            FROM `recurring_transactions`
        """)
        db.execSQL("DROP TABLE `recurring_transactions`")
        db.execSQL("ALTER TABLE `recurring_transactions_new` RENAME TO `recurring_transactions`")

        // ---- 4) quick_templates: amount(REAL) -> amount_cents(INTEGER) ----
        db.execSQL("""
            CREATE TABLE `quick_templates_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `amount_cents` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `accountId` INTEGER,
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `useCount` INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            INSERT INTO `quick_templates_new` (
                id, categoryId, amount_cents, type, description, accountId, sortOrder, useCount
            ) SELECT
                id, categoryId,
                CAST(ROUND(COALESCE(amount, 0) * 100) AS INTEGER),
                type, description, accountId, sortOrder, useCount
            FROM `quick_templates`
        """)
        db.execSQL("DROP TABLE `quick_templates`")
        db.execSQL("ALTER TABLE `quick_templates_new` RENAME TO `quick_templates`")
    }
}
```

> ⚠️ 逐列核对要点（血泪教训防御）：
> 1. `recurring_transactions` 的 `monthOfYear` 列由 MIGRATION_3_4:105 ALTER 加入，v7 实体亦声明——新表**必须含** `monthOfYear`，否则 `INSERT ... SELECT` 列不匹配。
> 2. 各表列顺序/NOT NULL/DEFAULT 须与 Room 依 v7 实体生成的定义**逐列一致**（含 `period TEXT NOT NULL`、`balance_cents ... DEFAULT 0`），否则升级后 Room schema 校验失败即闪退（参照 MIGRATION_4_5 注释的历史崩溃根因）。
> 3. `@ColumnInfo(name=...)` 决定列名——实体改名后列名须为 `amount_cents`/`balance_cents`，与本 SQL 完全对齐。
> 4. 注册：`addMigrations(..., MIGRATION_5_6, MIGRATION_6_7)`；`@Database(version = 7)`；`exportSchema=true` 会生成 `schemas/7.json`（须提交，供 MigrationTest 读取）。

### 1.4 各层改动清单与顺序

严格自底向上，每层改完可编译再进下一层（降低阶段三并行冲突面）：

1. **实体层（4 文件）**：`Budget.kt` / `Account.kt` / `RecurringTransaction.kt` / `QuickTemplate.kt`
   - `amount: Double` → `@ColumnInfo(name="amount_cents") val amountCents: Long`（account 为 `balance_cents`）
   - 需 `import androidx.room.ColumnInfo`
2. **DB 层**：`JianjiDatabase.kt`
   - `@Database(version = 6)` → `7`
   - 新增 `MIGRATION_6_7` 并加入 `addMigrations(...)`
   - **DAO 层零改动**（实测 `BudgetDao`/`AccountDao`/`RecurringTransactionDao`/`QuickTemplateDao` 均为实体级 CRUD，无 SQL 引用 amount/balance）
3. **Repository 层**：零改动（`BudgetRepository`/`AccountRepository` 直传实体）
4. **ViewModel 层（2 文件）**：
   - `BudgetViewModel.kt:18-20`：`getMonthlyBudget` 映射改 `(amountCents ?: 0L)/100.0`
   - `TransactionViewModel.kt:163`：`Math.round(rtx.amount*100)` → `rtx.amountCents`
5. **UI 层（4 文件）**：`SettingsScreen.kt`（718/907/1071 构造 + 906/1065 输入解析保留）、`JianjiApp.kt:206`、`HomeScreen.kt:340`、`AddTransactionDialog.kt:174/197`
   - 统一模式：**写入** `Math.round(amt*100)`，**读取展示** `amountCents/100.0`
6. **导入导出层**：`DataImportManager.kt`（见 §1.5）；`DataExportManager` **无需改**（CSV 仅导 Transaction，已是 `amountCents/100.0`）
7. **测试层**：新增 `MigrationTest`（依赖第 7 项设施，见 §1.6）

> 与阶段三协调：`JianjiDatabase.kt` 与 `SettingsScreen.kt` 是**共同热点文件**——建议本项在阶段三重构 merge 后，以「实体+DB 一批、UI 一批」两个小 PR 递进，避免大范围冲突。

### 1.5 导入/导出兼容策略

**结论：JSON `version` 从 2 升到 3，但 DTO 金额字段仍以「元 Double」承载，向后兼容旧备份。**

- **DTO 层（`*Import` data class）保持元 Double 不变**：`BudgetImport.amount` / `AccountImport.balance` / `RecurringImport.amount` / `TemplateImport.amount` 全部保留 `Double`。理由：JSON 是跨版本交换格式，用元为单位人类可读、且旧备份（version 缺省/1/2）里就是元 Double，改成分会破坏旧备份恢复。
- **映射层转换**：
  - 导出 `generateExportJson`：`amount = b.amountCents / 100.0`（4 处：budget/account/recurring/template）
  - 导入 `importFromJson`：构造实体时 `amountCents = Math.round(b.amount * 100)`（4 处）
  - Transaction 已是此模式（DataImportManager:127 导出 `t.amountCents/100.0`、:289 导入 `Math.round`），本项只是把另 4 张表拉齐。
- **version 语义**：
  - `version == 3`：本次（金额分存储的应用产出，DTO 仍元 Double——**JSON 结构不变**，version=3 仅作标记，方便未来诊断）
  - `version == 2`：旧全量备份，`isFull=true` 分支正常恢复（DTO 元 Double → 转分入库），**完全兼容**
  - `version == null`（旧交易+分类）：仅恢复两表，兼容不变
  - 判定逻辑 `val isFull = data.version == 2` 需放宽为 `data.version != null && data.version >= 2`，否则 version=3 备份会被当成旧格式只恢复两表（**关键修正点**）。
- **DataExportManager 无需改动**：其 CSV 导出仅涉及 Transaction，且已用 `transaction.amountCents / 100.0`；DB 文件备份（`createBackup`）是二进制整库拷贝，天然携带新 schema。

### 1.6 验收标准

1. **迁移正确性**：`MigrationTest` 用第 7 项设施覆盖 `6 → 7`——在 v6 库插入含小数金额的 budget/account/recurring/template 各 ≥1 条（如 12.34、0.01、99.99），执行 `MIGRATION_6_7` 后断言 `amount_cents`/`balance_cents` 分别等于 `Math.round(元*100)`（1234/1/9999）。
2. **往返恢复无精度漂移**：导出 JSON → 清库 → 导入，断言四类金额往返前后**逐条 `amountCents` 相等**（不经 Double 二次舍入丢分）。构造含 `10.005` 等边界值验证 `Math.round` 半分进位一致。
3. **升级不闪退**：v6 真实库升级 v7 后，Room `validateMigration` 通过（schema 逐列一致）；`fallbackToDestructiveMigration` 不应被触发（触发即说明 SQL 与实体不一致）。
4. **旧备份兼容**：用 version=2 旧备份恢复成功，金额正确入分。
5. **CI 编译通过**（本项目铁律：编译验证一律以 GitHub Actions CI 为准，禁止本地构建）。

---

## 2. 包名迁移评估（com.example.jianji → 正式包名）

实测当前：`app/build.gradle.kts:8 namespace = "com.example.jianji"`、`:12 applicationId = "com.example.jianji"`。

### 2.1 影响面：换 applicationId = 全新应用

- **应用身份**：`applicationId` 是 Android 应用在设备/商店的唯一标识。改它 = **系统视为一个全新 App**——不会覆盖安装，而是并存两个图标。
- **签名不互通**：即便同一签名 key，`applicationId` 不同也无法互相升级覆盖。
- **数据不互通**：应用私有目录 `/data/data/<applicationId>/` 随包名变化——旧包的数据库 `jianji_database`、备份文件（`filesDir/backups`、`filesDir/exports`）**新包一概读不到**。用户直接升级 = **数据清零观感**。
- **`namespace` vs `applicationId`**：`namespace` 影响生成的 `R`/`BuildConfig` 包与源码 package 路径（`com/example/jianji/...` 全量目录 + 每个 `.kt` 的 `package` 声明 + `import`）。`applicationId` 仅影响身份。二者可不同，但通常一起改。若只想改身份不动源码，可**只改 applicationId，保留 namespace**——工作量最小且不动数据层。

### 2.2 用户数据迁移引导方案（依赖阶段二备份能力）

由于数据物理隔离，唯一可靠路径是**旧包导出 → 新包导入**：

1. 发布**最后一版旧包（com.example.jianji）**，在设置页显著位置加「迁移到新版」引导：一键调用 `DataImportManager.generateExportJson`（version=3 全量 JSON），落地到用户可见的公共目录（Downloads/分享 sheet）。
2. 用户安装**新包**，首启引导「从备份恢复」→ 选中该 JSON → `importFromJson` 全量恢复。
3. 依赖阶段二的**备份/恢复能力**必须已稳定（全量 6 表原子恢复、旧版本兼容）——本方案不引入新数据通道，完全复用现有 JSON 备份。
4. 兜底：DB 文件备份（`createBackup` 二进制整库 `.db`）也可跨包，但需用户手动放置到新包目录，体验差，作为高级选项。

> 建议：若**尚未上架、无存量用户**，可直接改包名无需迁移引导；一旦有真实用户，则**必须**先发带导出引导的旧包过渡。

### 2.3 执行时机与配套改动

- **时机建议**：**仅在决定正式上架时执行**（与报告一致）。理由：改前无用户则零迁移成本；改后每次都要维护双包过渡，代价陡增。**上架前一次性定版**是最优窗口。
- **CI / 签名 / Release 配套**：
  1. `build.gradle.kts`：`namespace` + `applicationId` 改为正式包名（如 `com.<org>.jianji`）。
  2. **源码目录与 package 声明**：若改 `namespace`，需重命名 `app/src/main/java/com/example/jianji/` 整棵目录 + 全量 `.kt` 的 `package`/`import`（可 IDE Refactor > Rename package；本项目铁律禁本地构建，改完须 CI 验证）。
  3. **正式签名 keystore**：上架需正式 release 签名（非 debug）。CI 的 `build-apk.yml` 须注入 keystore（GitHub Secrets）并配 `signingConfigs`。
  4. **`AndroidManifest.xml`**：检查是否有硬编码 `com.example.jianji` 的 authority（如 FileProvider `${applicationId}` 占位符则无需改；硬编码则须改）。
  5. **Release 流程**：首个正式包版本号建议归一（如 v2.0.0）以示「全新上架应用」，Release Notes 明确「因包名变更需手动迁移数据」。

---

## 3. 风险评估表与工作量估算

### 3.1 风险评估

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| MIGRATION_6_7 列不对齐致升级闪退 | 中 | 高（老用户升级即崩） | 逐列核对 v7 实体；`exportSchema` 生成 `7.json`；MigrationTest 覆盖 6→7；保留 `fallbackToDestructiveMigration` 兜底但视为失败信号 |
| `recurring_transactions` 漏 `monthOfYear` 列 | 中 | 高 | §1.3 已在新表显式含该列 + `INSERT SELECT` 对齐；review checklist 强制核对 |
| version=3 备份被当旧格式只恢复两表 | 中 | 中（账户/预算/周期/模板丢失观感） | §1.5 将 `isFull` 判定放宽为 `version != null && >= 2` |
| Double→分 半分进位不一致 | 低 | 中 | 统一用 `Math.round(元*100)`（写）+ SQL `CAST(ROUND(...*100) AS INTEGER)`（迁）；边界值 10.005 测试 |
| 与阶段三在 `JianjiDatabase.kt`/`SettingsScreen.kt` 冲突 | 高 | 中 | 本项排在阶段三重构 merge 之后；拆「实体+DB」「UI」两小批递进 |
| 第 7 项迁移测试设施未就绪，MigrationTest 无处依附 | 高 | 中 | 强依赖登记；第 7 项落地前**不启动**本项测试；必要时先建 `androidTest` + `room-testing` 依赖 |
| 换包名致老用户数据「清零」 | 高（若无引导） | 高 | 上架前定版；有用户则先发带导出引导的过渡旧包（§2.2） |
| 换 namespace 全量 package 重命名引入编译错误 | 中 | 中 | IDE 自动 refactor；改完 CI 验证；或只改 applicationId 保留 namespace 降风险 |

### 3.2 工作量估算

| 子项 | 估算 | 说明 |
|------|------|------|
| 金额 v7 迁移——代码改动（23 处 / 12 文件） | 0.5 人日 | 实体 4 + DB 1 + VM 2 + UI 4 + import 1 |
| MIGRATION_6_7 SQL + schema 校验联调 | 0.5 人日 | 依赖 CI 往返验证 |
| MigrationTest + 往返恢复测试 | 0.5 人日 | **前置依赖第 7 项设施** |
| 金额迁移小计 | **~1.5 人日** | 不含阶段三 merge 等待 |
| 包名——仅改 applicationId（无用户场景） | 0.5 人日 | 含 CI 签名配置 |
| 包名——改 namespace 全量重命名 + 数据迁移引导 | 2~3 人日 | 含过渡旧包导出引导 + 新包恢复引导 + 双版本联调，**仅上架时** |

---

## 附录：关键实测结论速查

- 当前 DB version = **6**，`Transaction` 已 Long 分（范式样板：`MIGRATION_5_6`）。
- 待迁 4 字段：`budgets.amount` / `accounts.balance` / `recurring_transactions.amount` / `quick_templates.amount`，均 REAL NOT NULL。
- 实测 Double 金额有效改动点 **23 处 / 12 文件**（详见 §1.2）。
- 四表 DAO **零 SQL 引用金额列**，DAO/Repository 层无需改。
- 第 7 项迁移测试设施（androidTest / room-testing / MigrationTest）**当前不存在**，本项测试强依赖其先落地。
- 导出 JSON DTO 保留元 Double，`version` 升 3；`isFull` 判定须放宽为 `>= 2`。
- 包名：`namespace`=`applicationId`=`com.example.jianji`，换包名=全新应用、数据不互通，仅上架时执行。
