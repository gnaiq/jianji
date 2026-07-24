# 简记 (Jianji) — 安卓记账本

使用 **Kotlin** 和 **Jetpack Compose** 开发的现代化安卓记账应用，Material Design 3 风格。

## 功能特性

- **记账核心**：支持收入/支出记录，含分类、金额、日期、备注
- **分类管理**：11 个预设分类 + 自定义分类，图标/颜色/名称均可编辑
- **统计分析**：周/月/年三 Tab 切换，分类占比、收支趋势可视化
- **首页仪表盘**：今日收支概览、近 7 天收支趋势、月结余
- **搜索筛选**：按分类名称或备注关键词实时过滤当日记录
- **滑动删除**：首页列表支持 SwipeToDismiss 手势快速删除
- **本地存储**：基于 Room 数据库，离线可用
- **自动构建**：GitHub Actions 打 tag 自动构建 Release APK

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 1.9.24 |
| Jetpack Compose | 1.6.8 |
| Material Design 3 | 1.2.1 |
| Room Database | 2.6.1 |
| Navigation Compose | 2.7.7 |
| MPAndroidChart | 3.1.0 |
| Gradle | 8.9 |
| JDK | 17 |
| minSdk / targetSdk | 24 / 34 |

## 屏幕截图

> 截图待补充 — 欢迎提交 PR。

## 项目结构

```
jianji/
├── app/
│   ├── src/main/java/com/example/jianji/
│   │   ├── data/                         # 数据层
│   │   │   ├── Transaction.kt            #   交易实体
│   │   │   ├── Category.kt               #   分类实体
│   │   │   ├── TransactionDao.kt         #   交易 DAO
│   │   │   ├── CategoryDao.kt            #   分类 DAO
│   │   │   ├── TransactionRepository.kt  #   交易仓库
│   │   │   ├── CategoryRepository.kt     #   分类仓库
│   │   │   ├── JianjiDatabase.kt         #   Room 数据库
│   │   │   ├── DefaultCategories.kt      #   默认分类定义
│   │   │   └── Converters.kt             #   类型转换器
│   │   ├── ui/
│   │   │   ├── JianjiApp.kt              #   导航图 & 主入口 Composable
│   │   │   ├── screens/
│   │   │   │   ├── HomeScreen.kt         #   首页（今日概览 + 列表 + 搜索）
│   │   │   │   ├── StatisticsScreen.kt   #   统计（周/月/年 + 分类占比）
│   │   │   │   ├── CategoryManagementScreen.kt  # 分类管理
│   │   │   │   └── SettingsScreen.kt     #   设置（清除数据 / 关于）
│   │   │   ├── components/
│   │   │   │   └── AddTransactionDialog.kt  # 新增/编辑记录弹窗
│   │   │   ├── viewmodel/
│   │   │   │   ├── TransactionViewModel.kt
│   │   │   │   └── TransactionViewModelFactory.kt
│   │   │   └── theme/
│   │   │       ├── Color.kt / Theme.kt / Typography.kt
│   │   └── utils/
│   │       ├── DateUtils.kt
│   │       ├── StatisticsCalculator.kt
│   │       └── DataExportManager.kt       # 导出工具（功能开发中）
│   └── build.gradle.kts
├── .github/workflows/build-apk.yml        # CI：打 tag 自动构建 Release
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 快速开始

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

## 数据库模型

### Transaction（交易）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 自增主键 |
| categoryId | Long (FK) | 关联分类 |
| amount | Double | 金额 |
| type | TransactionType | INCOME / EXPENSE |
| description | String? | 备注 |
| date | LocalDateTime | 交易日期 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### Category（分类）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 自增主键 |
| name | String | 分类名称 |
| icon | String | 图标（Emoji） |
| color | String | 颜色（十六进制） |
| type | CategoryType | INCOME / EXPENSE |
| isDefault | Boolean | 是否系统预设 |

## 默认分类

**收入（4 个）**：💼 工资 · 🎁 奖金 · 📈 投资收益 · 💰 其他收入

**支出（7 个）**：🍔 食物 · 🚗 交通 · 🎮 娱乐 · 🛍️ 购物 · 🏥 医疗 · 📚 教育 · 💸 其他支出

## GitHub Actions 自动化

打 `v*` 标签或手动触发 `workflow_dispatch` 时自动：

1. 检出代码 → 设置 JDK 17 → 构建 Release APK
2. 按 `jianji-{tag}.apk` 命名并上传为 Artifact
3. 自动创建 GitHub Release 并附上 APK

### 手动触发

```bash
# 创建并推送 annotated tag
git tag -a v1.0.0 -m "Release v1.0.0"
# 推送到 GitHub 后 CI 自动构建发布
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

## 安装

从 [GitHub Releases](https://github.com/gnaiq/jianji/releases) 下载最新 APK，或从源码构建：

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

MIT License

## 更新日志

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
