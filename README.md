# 简记 Jianji

> 一款用 **Kotlin + Jetpack Compose** 打造的现代化 Android 记账应用，Material Design 3 风格，完全本地存储、离线可用。

<p align="center">
  <a href="https://github.com/gnaiq/jianji/releases"><img alt="Latest Release" src="https://img.shields.io/github/v/release/gnaiq/jianji?label=release&color=green"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84">
  <img alt="License" src="https://img.shields.io/github/license/gnaiq/jianji">
  <img alt="Build" src="https://img.shields.io/github/actions/workflow/status/gnaiq/jianji/build-apk.yml?branch=main">
  <img alt="Kotlin" src="https://img.shields.io/badge/kotlin-1.9.24-blue">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-24%20%2F%20targetSdk-34-blue">
</p>

---

## 📑 目录

- [简介](#简介)
- [✨ 功能特性](#-功能特性)
- [📱 屏幕截图](#-屏幕截图)
- [🛠 技术栈](#-技术栈)
- [📂 项目结构](#-项目结构)
- [🚀 快速开始](#-快速开始)
- [🗄 数据模型](#-数据模型)
- [🏷 默认分类](#-默认分类)
- [🤖 GitHub Actions 自动化](#-github-actions-自动化)
- [📦 安装](#-安装)
- [🤝 贡献](#-贡献)
- [📄 许可证](#-许可证)
- [📝 更新日志](#-更新日志)

---

## 简介

**简记（Jianji）** 是一款轻量、隐私优先的安卓记账本：所有数据保存在设备本地 Room 数据库，不依赖任何云端账户。它覆盖从日常记账、多账户与转账、分类/标签管理、预算控制、统计分析、日历视图到年度账单海报、周期交易、数据备份导出的完整闭环，并支持桌面小组件一键记账概览。

---

## ✨ 功能特性

### 记账核心
- **收入 / 支出 / 转账** 三种交易类型；金额内部以「分」（Long）存储，无浮点误差。
- **记账弹窗**：分类选择、账户选择（转账选转出/转入账户并显示余额）、日期 + 时间选择、内置**计算器键盘**（支持 +−×÷ 表达式求值）、标签多选、备注（≤100 字）、快捷模板一键填充。
- **快捷模板**：常用记账一键复用，按使用频次排序，首页展示 Top 8。
- **周期交易**：按 日 / 周 / 月 / 年 频率自动记账（如房租、工资），支持间隔周期，突出下次记账日。

### 账户与预算
- **多账户管理**：现金、银行卡、支付宝等自定义账户（图标可选），支持默认账户与余额跟踪。
- **账户间转账**：TRANSFER 类型 + 转出/转入账户，系统内置「转账」分类。
- **月度预算**：设置当月总预算，首页进度条实时显示；使用超 80% 转橙色、超支转红色并显示超支金额。

### 浏览与检索
- **首页仪表盘**：本月收支/结余卡片、月度预算进度、今日支出、快捷模板入口、近 7 天趋势、按日交易列表（含账户名、转账箭头、标签 chip）。
- **历史账单页**：按日期分组，文本搜索 + **高级筛选**（类型 / 账户 / 分类多选 / 标签多选 / 金额区间 / 日期区间）。
- **日历视图**：月历网格显示每日收支汇总，点击某日弹出当日交易明细，今日高亮。
- **滑动删除 + 回收站**：删除为**软删除**，Snackbar 可撤销；回收站支持单条还原 / 彻底删除 / 清空。

### 分类与标签
- **层级分类**：默认分类树（生活 / 交通 / 孩子 / 其他 + 收入类）+ 自定义分类，图标（Emoji）/ 颜色 / 名称均可编辑。
- **标签系统**：交易与标签多对多关联；标签管理页支持增删改，40 个 Emoji 图标 + 12 色调色板可选。

### 统计分析
- **周 / 月 / 年**三 Tab 切换（可翻页 + 选日期），收支概览、分类占比（支出/收入明细带百分比）。
- **趋势折线图**（MPAndroidChart，支出/收入双线），数据点可点击查看费用数字；年统计含近五年年度趋势；每日趋势表（最近 14 天，含净额）。
- **年度账单海报**：按年份生成 1080×1920 海报（净结余、收支笔数、月度支出柱状图、消费亮点），写入媒体库公共目录，相册可见、可分享。

### 数据安全
- **本地存储**：基于 Room 数据库（schema v6，完整迁移链），离线可用，隐私不出本机。
- **备份与恢复**：JSON 全量备份（6 张表、单事务原子恢复）、CSV / **Excel（XLSX）** 导出；「管理备份」可清理旧备份文件。
- **自动备份**：定时（每天 / 每周 / 每月）+ 数据变更即时备份（防抖 5s）双机制；备份写入共享下载目录，**卸载后仍可恢复**。
- **应用内更新**：检查 GitHub Releases、下载进度显示、可取消；安装前自检包名 / 版本 / 签名，签名不一致或降级时给出明确中文告警。

### 其他
- **深色模式**：跟随系统 / 浅色 / 深色三挡切换。
- **桌面小组件**：Glance AppWidget 在桌面展示记账概览，数据变化自动刷新。
- **自动构建**：GitHub Actions 打 tag 自动构建 Release APK。

---

## 📱 屏幕截图

> 截图待补充 — 欢迎提交 PR 补充应用界面截图，帮助更多用户快速了解简记。

---

## 🛠 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 1.9.24 |
| Jetpack Compose | 1.6.8 |
| Material Design 3 | 1.2.1 |
| Room Database（KSP） | 2.6.1 |
| Navigation Compose | 2.7.7 |
| MPAndroidChart | 3.1.0 |
| Glance AppWidget | 1.1.0 |
| Gson | 2.10.1 |
| kotlinx-serialization-json | 1.6.3 |
| Apache Commons CSV | 1.10.0 |
| desugar_jdk_libs（java.time on minSdk 24） | 2.0.4 |
| Gradle | 8.9 |
| JDK | 17 |
| minSdk / targetSdk | 24 / 34 |

> Excel（XLSX）导出为**零依赖自实现**（ZIP + XML 直接拼装），未引入 POI / JXL。

---

## 📂 项目结构

```
jianji/
├── app/
│   ├── src/main/java/com/example/jianji/
│   │   ├── data/                         # 数据层（Room schema v6，8 个实体）
│   │   │   ├── Transaction.kt            #   交易实体（金额以分存储、软删除、转账）
│   │   │   ├── Category.kt               #   分类实体（支持父子层级 parentId）
│   │   │   ├── Account.kt                #   账户实体（余额、默认账户）
│   │   │   ├── Budget.kt                 #   预算实体（月度/年度、总/分类预算）
│   │   │   ├── Tag.kt                    #   标签实体
│   │   │   ├── TransactionTagCrossRef.kt #   交易-标签多对多关联表
│   │   │   ├── QuickTemplate.kt          #   快捷记账模板
│   │   │   ├── RecurringTransaction.kt   #   周期交易（日/周/月/年）
│   │   │   ├── SearchFilters.kt          #   高级筛选条件模型
│   │   │   ├── *Dao.kt / *Repository.kt  #   各实体 DAO 与仓库
│   │   │   ├── JianjiDatabase.kt         #   Room 数据库（含 MIGRATION_1_2 … 5_6）
│   │   │   ├── DefaultCategories.kt      #   默认分类树定义
│   │   │   └── Converters.kt             #   类型转换器
│   │   ├── ui/
│   │   │   ├── JianjiApp.kt              #   导航图 & 主入口（底部 5 Tab + 次级路由）
│   │   │   ├── screens/
│   │   │   │   ├── HomeScreen.kt         #   首页（概览 + 预算 + 模板 + 列表）
│   │   │   │   ├── StatisticsScreen.kt   #   统计（周/月/年 + 图表）
│   │   │   │   ├── HistoryScreen.kt      #   历史账单（搜索 + 高级筛选）
│   │   │   │   ├── CalendarScreen.kt     #   日历视图
│   │   │   │   ├── CategoryManagementScreen.kt  # 分类管理
│   │   │   │   ├── TagsScreen.kt         #   标签管理
│   │   │   │   ├── RecycleBinScreen.kt   #   回收站（还原/彻底删除/清空）
│   │   │   │   └── SettingsScreen.kt     #   设置（数据/功能/外观/更新）
│   │   │   ├── components/
│   │   │   │   ├── AddTransactionDialog.kt  # 记账弹窗（计算器键盘/账户/标签）
│   │   │   │   └── TagFormDialog.kt      #   标签编辑弹窗
│   │   │   ├── viewmodel/                #   TransactionViewModel & Factory
│   │   │   └── theme/                    #   Color / Theme / Typography
│   │   ├── widget/                       #   Glance 桌面小组件
│   │   └── utils/
│   │       ├── DateUtils.kt / StatisticsCalculator.kt
│   │       ├── DataExportManager.kt      #   CSV/JSON 导出
│   │       ├── DataImportManager.kt      #   JSON 备份导入（原子恢复）
│   │       ├── ExcelExportManager.kt     #   XLSX 导出（零依赖自实现）
│   │       ├── AutoBackup.kt / BackupScheduler.kt / BackupStorage.kt
│   │       ├── AutoBackupReceiver.kt / AutoBackupTriggerReceiver.kt
│   │       ├── PosterGenerator.kt        #   年度账单海报
│   │       ├── RecurringSchedule.kt      #   周期交易调度
│   │       ├── UpdateManager.kt / VersionComparator.kt  # 应用内更新
│   │       └── AppPrefs.kt               #   偏好（深色模式等）
│   └── build.gradle.kts
├── .github/workflows/build-apk.yml        # CI：打 tag 自动构建 Release
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 快速开始

### 环境要求

- Android Studio 2023.1+
- JDK 17+
- Android SDK 34+

### 构建

```bash
git clone https://github.com/gnaiq/jianji.git
cd jianji

# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

---

## 🗄 数据模型

Room 数据库 schema **v6**，共 8 个实体，迁移链 `MIGRATION_1_2` → `5_6` 完整保留历史数据。

### Transaction（交易）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 自增主键 |
| categoryId | Long (FK) | 关联分类 |
| amountCents | Long | 金额（**分**，避免浮点误差） |
| type | TransactionType | INCOME / EXPENSE / TRANSFER |
| description | String? | 备注 |
| date | LocalDateTime | 交易日期 |
| accountId | Long? | 所属账户（转账时为转出账户） |
| toAccountId | Long? | 转入账户（仅 TRANSFER） |
| deletedAt | LocalDateTime? | 软删除时间（null = 未删除） |
| createdAt / updatedAt | LocalDateTime | 创建 / 更新时间 |

### Category（分类）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 自增主键 |
| name | String | 分类名称 |
| icon | String | 图标（Emoji） |
| color | String | 颜色（十六进制） |
| type | CategoryType | INCOME / EXPENSE |
| parentId | Long? | 父分类（支持层级） |
| isDefault | Boolean | 是否系统预设 |

### 其他实体

| 实体 | 关键字段 | 说明 |
|------|---------|------|
| Account | name / icon / balance / isDefault | 多账户与余额 |
| Budget | categoryId? / amount / period(MONTHLY·YEARLY) / year / month | categoryId 为 null 表示总预算 |
| Tag | name / color / icon / sortOrder | 标签，与交易多对多（transaction_tags 关联表） |
| QuickTemplate | categoryId / amount / type / accountId? / useCount | 快捷记账模板，按使用频次排序 |
| RecurringTransaction | frequency(DAILY·WEEKLY·MONTHLY·YEARLY) / interval / nextRunDate / isActive | 周期交易 |

---

## 🏷 默认分类

默认分类为**两级树形结构**：

- **生活**：租房 · 水电煤气 · 餐饮 · 水果 · 零食 · 购物 · 食材 · 通讯 · 日用品 · 电子产品 · 理发 · 医疗 · 运动
- **交通**：公用交通 · 汽车加油 · 充电 · 保养 · 维修 · 过路费 · 停车费 · 车险 · 违章
- **孩子**：玩具 · 教育
- **其他**：旅游 · 社交 · 人情往来 · 学习提升
- **收入**：💼 工资 · 🎁 奖金 · 📈 投资收益 · 💰 其他收入

另有系统内置「转账」分类（用于账户间转账，不可删除）。

---

## 🤖 GitHub Actions 自动化

打 `v*` 标签或手动触发 `workflow_dispatch` 时自动：

1. 检出代码 → 设置 JDK 17 → 构建 Release APK
2. 按 `jianji-{tag}.apk` 命名并上传为 Artifact
3. 自动创建 GitHub Release 并附上 APK

### 手动触发

```bash
# 创建 annotated tag
git tag -a v1.0.0 -m "Release v1.0.0"
# 推送 tag 到 GitHub 后 CI 自动构建发布（只建本地 tag 不会触发 CI）
git push origin v1.0.0
```

### 版本号纪律（防止升级失败）

> 曾在 v1.4.10→v1.4.11 踩坑：设备上装了**本地/调试构建**的高 `versionCode` 包，应用内更新下载正式版后因系统判定“已安装更高版本（降级）”而安装失败。

防再次发生，发布务必遵守：

1. **`versionCode` 只能递增、绝不回退**。每次发版 `+1`。`build-apk.yml` 已加守卫：若本次 `versionCode` 不大于上一个 release tag，CI 直接失败，阻断发布降级版本。
2. **APK 只能由 CI 构建发布**（`gradle assembleRelease` 仅允许在 GitHub Actions 跑）。本地 `./gradlew assembleRelease|Debug` 产出的包若被侧载安装，其 `versionCode`/签名可能与正式版冲突，导致后续升级失败。**严禁把本地构建的 APK 安装到日常使用的设备**。
3. 若设备上已装了本地/调试包导致升级报“已安装更高版本”：先在系统设置里**卸载**该应用，再从 GitHub Releases 安装正式版（应用内更新也会在 v1.4.11+ 检测“签名不一致/降级”并给出明确提示）。
4. `versionCode` 唯一来源是 `app/build.gradle.kts`，不要在任何脚本里另设。

### 一键发版脚本 `release.sh`

> 本仓库已附带 `release.sh`，把上面 4 条纪律封装成可重复执行的一条命令（自动 `versionCode+1`、`versionName` 同步、annotated tag 两步法、触发 CI、字节级校验）。**本机 `git push` 被 TLS 代理拦截**时，用它绕过，照常发版。

```bash
# 1) 把本地改动推到 main（替代不可用的 git push，走 GitHub Contents API，自动乐观锁）
./release.sh push <file>...

# 2) 一键发版：自动 patch+1 提版本 → 打 annotated tag → 触发 CI → 验证 Release
./release.sh cut

# 3) 发完后字节级校验 APK 的 versionCode（防止降级包被发出）
./release.sh verify v1.4.12
```

- `push <file>...`：逐个文件 PUT 到 `main`，自动区分新建/更新（拿 blob sha 做乐观锁）；多文件用空格分隔。
- `cut`：读取 `app/build.gradle.kts`，将 `versionCode` 与 `versionName` 自动 `+1`，创建 **annotated tag**（两步法 `POST /git/tags` → `POST /git/refs`，非 lightweight）触发 CI，轮询至 `success` 后输出 Release 资产清单。
- `verify <vX.Y.Z>`：下载指定 tag 的 APK，字节级确认 `versionCode`，与第 1 条纪律相互兜底。

> 能正常 `git push` 的环境无需脚本，直接 `git tag -a v1.x.y -m "..." && git push origin v1.x.y` 即可，二者等价（CI 守卫与 Release 流程相同）。

## 📦 安装

从 [GitHub Releases](https://github.com/gnaiq/jianji/releases) 下载最新 APK，或从源码构建：

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```

> 安装未知来源 APK 时，系统会要求授予「安装未知应用」权限；简记使用 `FileProvider` 安全安装，不暴露明文文件路径。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

1. Fork 本仓库并创建特性分支（`git checkout -b feature/your-feature`）
2. 提交改动（`git commit -m "feat: ..."`）
3. 推送到分支（`git push origin feature/your-feature`）
4. 发起 Pull Request

---

## 📄 许可证

[MIT License](LICENSE)

---

## 📝 更新日志

### v1.6.10 (2026-07)

- **记账弹窗**：细节修复与体验优化
- **预算**：修复「第二次修改预算不生效」（`setBudget` 改为按主键 upsert）
- **标签表单**：编辑体验优化
- **数据库**：修复 v1.6.6 升级路径 `MIGRATION_5_6` 可能导致的升级崩溃（重建 transactions 表以匹配 Room v6 schema）

### v1.6.6 (2026-07)

- **金额精度**：金额存储从 Double 迁移为 **Long（分）**，彻底消除浮点误差（MIGRATION_5_6 自动迁移）
- **标签系统**：交易可打多个标签（多对多），标签管理页 + 40 Emoji / 12 色编辑
- **软删除 + 回收站**：删除可撤销（Snackbar），回收站支持还原 / 彻底删除 / 清空
- **日历视图**：月历网格展示每日收支，点击查看当日明细
- **记账弹窗**：内置计算器键盘（+−×÷ 表达式求值）、标签多选

### v1.6.4 – v1.6.5 (2026-07)

- **高级筛选**：历史页支持 类型 / 账户 / 分类多选 / 标签多选 / 金额区间 / 日期区间 组合筛选
- **设置页**：重组为 数据管理 / 功能管理 / 外观 / 关于&更新 四分组，新增 Excel（XLSX）导出

### v1.6.3 (2026-07)

- **账户间转账**：新增 TRANSFER 交易类型，选择转出/转入账户，内置「转账」系统分类

### v1.6.0 – v1.6.2 (2026-07)

- **层级分类**：分类支持父子两级（默认分类树：生活/交通/孩子/其他）
- **历史账单页**：新增底部导航「历史」Tab，按日期分组 + 搜索
- **自动备份增强**：数据变更即时备份（防抖 5s）+ 定时备份双机制

### v1.5.0 – v1.5.1 (2026-07)

- **周期交易自动执行**：按 日/周/月/年 频率自动入账（RecurringSchedule 调度）
- **深色模式**：跟随系统 / 浅色 / 深色三挡
- **应用内更新**：语义化版本比较（VersionComparator），下载进度与取消
- **单元测试**：新增 RecurringSchedule / StatisticsCalculator / VersionComparator 测试

### v1.4.6 (2026-07-24)

- **检查更新（修复下载失败）**：弃用 `DownloadManager`（其 `file://` 目标 URI 在 Android 7+ 触发 `FileUriExposedException`/目标校验异常，表现为“下载失败：com.example.jianji:One of…”）；改用 `HttpURLConnection` 直接下载 APK 并经 FileProvider 安装，错误提示改为可读中文
- **移除 App 锁**：删除指纹/PIN 应用锁功能（含 `AppLockManager`、锁门禁、`biometric`/`fragment-ktx` 依赖与 `USE_BIOMETRIC` 权限）
- **管理备份**：新增“管理备份”入口，可列出并删除下载目录中的旧备份文件（防备份堆积），删除前二次确认

### v1.4.5 (2026-07-23)

- **统计**：折线图点击数据点显示该时段费用数字
- **年度账单海报**：年份可选（数据年份 + 当前年）；图片写入媒体库公共目录，相册可见、可正常分享打开
- **检查更新**：失败时复用本机已下载安装包直接安装，并提供 GitHub 手动下载兜底
- **周期交易**：添加表单独立聚焦（卡片化、突出下次记账日、取消 / 添加分明）
- **备份**：Android 10 以下写公共 Download 目录；数据变化时自动备份到共享目录，卸载后仍可恢复

### v1.1.4 (2026-07-23)

- **首页**：今日收支概览 + 近 7 天趋势 + 月结余显示
- **搜索**：首页按分类名称/备注实时过滤
- **滑动删除**：SwipeToDismiss 手势快速删除记录
- **分类管理**：新增/编辑/删除自定义分类，支持图标与颜色编辑
- **统计页**：周/月/年三 Tab，分类占比 drill-down，月度收支趋势
- **版本号统一**：设置页 `BuildConfig.VERSION_NAME` 动态读取

### v1.0.0 (2024-01-15)

- 初始版本：核心记账、预设分类、周/月/年统计、GitHub Actions CI
