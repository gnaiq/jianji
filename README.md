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

### v1.1.4 (2026-07-23)

- **首页**：今日收支概览 + 近 7 天趋势 + 月结余显示
- **搜索**：首页按分类名称/备注实时过滤
- **滑动删除**：SwipeToDismiss 手势快速删除记录
- **分类管理**：新增/编辑/删除自定义分类，支持图标与颜色编辑
- **统计页**：周/月/年三 Tab，分类占比 drill-down，月度收支趋势
- **版本号统一**：设置页 `BuildConfig.VERSION_NAME` 动态读取

### v1.0.0 (2024-01-15)

- 初始版本：核心记账、预设分类、周/月/年统计、GitHub Actions CI
