# 简记 (Jianji) - 安卓记账本应用

一个使用 **Kotlin** 和 **Jetpack Compose** 开发的现代化安卓记账应用。

## 功能特性

- ✅ **记账功能**：支持收入和支出记录
- ✅ **分类管理**：预设分类，支持自定义分类
- ✅ **周/月/年统计**：可视化统计图表
- ✅ **本地数据库**：使用 Room 数据库存储数据
- ✅ **数据备份与恢复**：本地备份功能
- ✅ **数据导出**：支持 CSV 和 Excel 格式导出
- ✅ **现代 UI**：基于 Jetpack Compose 和 Material Design 3
- ✅ **自动构建**：GitHub Actions 自动化构建和发布

## 技术栈

- **语言**：Kotlin 1.9.24
- **UI 框架**：Jetpack Compose 1.6.8
- **数据库**：Room 2.6.1
- **导航**：Jetpack Navigation Compose 2.7.7
- **图表**：MPAndroidChart 3.1.0
- **数据导出**：Apache Commons CSV, Apache POI
- **最低 API 级别**：24
- **目标 API 级别**：34

## 项目结构

```
jianji/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/jianji/
│   │   │   │   ├── data/              # 数据层 (Entity, DAO, Database, Repository)
│   │   │   │   ├── ui/                # UI 层 (Compose screens, theme)
│   │   │   │   ├── ui/viewmodel/      # ViewModel 层
│   │   │   │   └── MainActivity.kt    # 主入口
│   │   │   └── res/                   # 资源文件
│   │   ├── androidTest/               # 安卓测试
│   │   └── test/                      # 单元测试
│   ├── build.gradle.kts               # App 级别构建配置
│   └── proguard-rules.pro             # ProGuard 混淆规则
├── build.gradle.kts                   # 项目级别构建配置
├── settings.gradle.kts                # 项目设置
├── gradle.properties                  # Gradle 属性
├── .github/workflows/                 # GitHub Actions 工作流
└── README.md                          # 本文件
```

## 快速开始

### 环境要求

- Android Studio 2023.1 或更高版本
- JDK 17 或更高版本
- Android SDK 34 或更高版本

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/gnaiq/jianji.git
cd jianji

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 运行测试
./gradlew test

# 安装到设备
./gradlew installDebug
```

## 数据库架构

### Transaction 表
- `id`：主键
- `categoryId`：分类 ID（外键）
- `amount`：金额
- `type`：类型（INCOME/EXPENSE）
- `description`：描述
- `date`：交易日期
- `createdAt`：创建时间
- `updatedAt`：更新时间

### Category 表
- `id`：主键
- `name`：分类名称
- `icon`：图标（Emoji）
- `color`：颜色（十六进制）
- `type`：类型（INCOME/EXPENSE）
- `isDefault`：是否为默认分类

## 功能详解

### 1. 记账功能
- 快速添加收入/支出记录
- 支持选择分类、金额、日期、描述
- 编辑和删除已有记录

### 2. 分类管理
- 预设 11 个常用分类（收入 4 个，支出 7 个）
- 支持自定义分类
- 分类支持自定义图标和颜色

### 3. 统计分析
- 周统计：显示本周收入/支出总额
- 月统计：显示本月收入/支出总额及分类占比
- 年统计：显示全年收入/支出趋势
- 支持柱状图、饼图等可视化展示

### 4. 数据备份与恢复
- 支持本地数据库备份
- 支持从备份恢复数据
- 备份文件存储在应用私有目录

### 5. 数据导出
- **CSV 导出**：导出为 CSV 格式，可在 Excel 中打开
- **Excel 导出**：直接导出为 .xlsx 格式

## GitHub Actions 自动化

### 工作流配置

项目包含自动化构建和发布工作流：

- **触发条件**：推送到 `main` 分支或创建 Release
- **构建步骤**：
  1. 检出代码
  2. 设置 JDK 17
  3. 构建 Release APK
  4. 上传 APK 到 Release
- **输出**：Release APK 安装包

### 手动触发构建

```bash
# 创建 Release 标签
git tag v1.0.0
git push origin v1.0.0

# GitHub Actions 会自动构建并发布 APK
```

## 安装应用

### 方式 1：从 GitHub Release 下载

1. 访问 [Releases](https://github.com/gnaiq/jianji/releases)
2. 下载最新的 APK 文件
3. 在 Android 设备上安装

### 方式 2：从源码构建

```bash
./gradlew assembleRelease
# APK 位于 app/build/outputs/apk/release/
```

## 开发指南

### 添加新功能

1. 在 `data/` 中定义数据模型和 DAO
2. 在 `data/` 中创建 Repository
3. 在 `ui/viewmodel/` 中创建 ViewModel
4. 在 `ui/screens/` 中创建 Compose 屏幕
5. 在 `JianjiApp.kt` 中注册导航

### 代码风格

- 遵循 Kotlin 官方代码风格指南
- 使用 Compose 最佳实践
- 添加必要的注释和文档

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 作者

- **gnaiq** - 项目创建者

## 更新日志

### v1.0.0 (2024-01-15)
- 初始版本发布
- 实现核心记账功能
- 支持分类管理
- 支持周/月/年统计
- 支持数据备份导出
- 配置 GitHub Actions 自动构建

## 常见问题

### Q: 如何导出数据？
A: 在设置页面选择"导出为 CSV"或"导出为 Excel"，数据将保存到设备的下载文件夹。

### Q: 如何备份数据？
A: 在设置页面点击"备份数据"，备份文件将保存到应用私有目录。

### Q: 支持云同步吗？
A: 当前版本仅支持本地存储，未来版本可能添加云同步功能。

## 联系方式

- GitHub: [@gnaiq](https://github.com/gnaiq)
- Issues: [提交 Issue](https://github.com/gnaiq/jianji/issues)
