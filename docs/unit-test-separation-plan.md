# 简记 单元测试独立化实施方案

> 基线版本：v1.6.26 (versionCode 59)
> 方案日期：2026-08-03
> 原则：纯 JVM 单元测试从 `:app` 模块中完全分离为独立 Gradle 子项目，可脱离主项目编译运行；androidTest（仪器化测试）因依赖 Android 框架，保留在 `:app` 内但独立归类

---

## 一、现状摸底

### 现有测试文件

| 文件 | 位置 | 类型 | 行数 | 依赖框架 |
|------|------|------|------|---------|
| `RecurringScheduleTest.kt` | `app/src/test/` | JVM 单元 | 38 | JUnit 4 |
| `StatisticsCalculatorTest.kt` | `app/src/test/` | JVM 单元 | 42 | JUnit 4 |
| `VersionComparatorTest.kt` | `app/src/test/` | JVM 单元 | 32 | JUnit 4 |
| `ImportParsingToleranceTest.kt` | `app/src/test/` | JVM 单元 | 90 | JUnit 4 + kotlinx-coroutines |
| `DataImportManagerTest.kt` | `app/src/androidTest/` | 仪器化 | 319 | JUnit 4 + Room + Espresso |

### 现有测试依赖

```kotlin
// app/build.gradle.kts
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.room:room-testing:2.6.1")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
```

### 现有 CI 配置

- 单元测试：`./gradlew testDebugUnitTest`（ubuntu-latest runner，JVM 直接运行）
- 仪器化测试：`./gradlew connectedCheck`（ubuntu-latest + Android 模拟器）

### 关键约束

- `:app` 使用 `com.android.application` 插件，**不可被纯 JVM 模块依赖**
- 现有单元测试直接 import 生产代码类（如 `com.example.jianji.data.RecurringFrequency`），分离后需解决类路径访问问题

---

## 二、架构选型

### 方案对比

| 方案 | 独立性 | 改动量 | 可维护性 | 推荐 |
|------|--------|--------|----------|------|
| A. 新建 `:core` 库模块 + `:testing` 测试模块 | ★★★★★ | 大（需迁移所有纯 Kotlin 类到 core） | ★★★★★ | 长期 |
| B. 新建 `:testing` 子项目，通过 Gradle 复合构建引用 `:app` 源码 | ★★★★ | 中 | ★★★★ | **本次采用** |
| C. 仅重组 `app/src/test/` 目录结构 | ★★ | 小 | ★★★ | 不推荐 |

### 推荐方案：B — Gradle 复合构建 + 独立测试子项目

**核心思路**：创建 `testing/` 子项目，使用 Gradle 的 `includeBuild` 或直接引用 `:app` 的源码路径，使测试代码在独立模块中编译运行，同时保持对生产代码的零侵入访问。

**不选 C 的原因**：仍与主项目共享同一 Gradle task 图，无法独立运行。

**不选 A 的原因**：需将 data/utils/viewmodel 层全部抽出到 `:core` 模块，涉及 20+ 文件迁移，改动面过大，且 P0/P1 方案在并行推进，不宜同时做大重构。

**方案 B 的关键技术路径**：

```
testing/
├── build.gradle.kts          # 独立构建配置，引用 app 源码
├── settings.gradle.kts       # 独立 settings（可选，用于完全独立运行）
├── run-tests.sh              # 独立执行脚本
└── src/test/java/com/example/jianji/
    ├── data/                  # 数据层测试
    ├── viewmodel/             # ViewModel 测试
    └── utils/                 # 工具类测试
```

通过 `sourceSets` 将 `:app` 的 `src/main/java` 和 `src/test/java` 分别引入，使测试模块能访问生产代码的同时保持独立构建。

---

## 三、目录结构与文件组织

### 3.1 顶层目录

```
root/
├── app/                          # 主项目（不动）
│   ├── src/main/                 # 生产代码
│   ├── src/androidTest/          # 仪器化测试（保留）
│   └── build.gradle.kts
├── testing/                      # ★ 独立单元测试项目
│   ├── build.gradle.kts
│   ├── settings.gradle.kts       # 独立 settings（可选）
│   ├── run-tests.sh
│   ├── config/
│   │   └── test-dependencies.toml
│   └── src/test/java/com/example/jianji/
│       ├── data/
│       ├── viewmodel/
│       ├── utils/
│       └── fixtures/             # 共享测试夹具
├── docs/
│   └── unit-test-separation-plan.md
└── settings.gradle.kts           # 根 settings（不动）
```

### 3.2 测试文件命名规范

| 被测目标 | 命名格式 | 示例 |
|----------|---------|------|
| 类/接口 | `{ClassName}Test.kt` | `TransactionViewModelTest.kt` |
| 纯函数/工具方法 | `{UtilName}Test.kt` | `VersionComparatorTest.kt` |
| 集成场景（多类协作） | `{FeatureName}IntegrationTest.kt` | `BudgetMigrationIntegrationTest.kt` |
| 参数化测试 | `{ClassName}ParameterizedTest.kt` | `RecurringScheduleParameterizedTest.kt` |

**规则**：
- 测试文件与生产文件同名 + `Test` 后缀，一一对应
- 一个测试文件只测一个生产类，禁止「大杂烩」测试文件
- 测试类名 = 文件名（Kotlin 惯例）

### 3.3 目录层级划分（镜像生产代码结构）

```
testing/src/test/java/com/example/jianji/
├── data/                              # 数据层测试
│   ├── BudgetDaoTest.kt               # DAO 方法（纯 SQL 验证，不依赖 Room）
│   ├── TransactionDaoTest.kt
│   ├── TransactionRepositoryTest.kt   # Repository 逻辑
│   ├── BudgetRepositoryTest.kt
│   ├── CategoryRepositoryTest.kt
│   ├── AccountRepositoryTest.kt
│   ├── TagRepositoryTest.kt
│   ├── RecurringTransactionRepositoryTest.kt
│   └── ConvertersTest.kt              # TypeConverter 测试
├── viewmodel/                         # ViewModel 层测试
│   ├── TransactionViewModelTest.kt    # P0-1
│   ├── BudgetViewModelTest.kt         # P0-3
│   ├── AccountViewModelTest.kt
│   ├── CategoryViewModelTest.kt
│   ├── SettingsViewModelTest.kt
│   └── TagViewModelTest.kt
├── utils/                             # 工具类测试
│   ├── RecurringScheduleTest.kt       # [已有] 迁移
│   ├── StatisticsCalculatorTest.kt    # [已有] 迁移
│   ├── VersionComparatorTest.kt       # [已有] 迁移
│   ├── ImportParsingToleranceTest.kt  # [已有] 迁移
│   ├── DateUtilsTest.kt
│   ├── BackupStorageTest.kt
│   └── AppPrefsTest.kt
└── fixtures/                          # 共享测试夹具
    ├── TestDataFactory.kt             # 标准测试数据工厂
    ├── FakeDaos.kt                    # Fake DAO 实现（替代 Room 内存数据库）
    └── CoroutineTestRules.kt          # 协程测试规则
```

### 3.4 测试分类方式

| 分类维度 | 标注方式 | 用途 |
|----------|---------|------|
| **按模块** | 目录层级 `data/` `viewmodel/` `utils/` | 日常开发定位 |
| **按速度** | JUnit `@Category` 或自定义注解 `@FastTest` `@SlowTest` | CI 分级执行 |
| **按类型** | 命名后缀 `Test` vs `IntegrationTest` | 区分单元/集成 |
| **按优先级** | 自定义注解 `@P0` `@P1` `@P3` | 与缺陷优先级对齐 |

**CI 执行策略**：
```
./gradlew :testing:test --tests "*Test"          # 快速反馈（仅单元测试）
./gradlew :testing:test --tests "*IntegrationTest"  # 完整验证（含集成测试）
```

---

## 四、依赖管理

### 4.1 `testing/build.gradle.kts` 完整配置

```kotlin
plugins {
    kotlin("jvm") version "1.9.24"
}

group = "com.example.jianji.testing"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// ===== 核心：引用主项目源码 =====
// 策略：将 app/src/main/java 作为测试模块的编译依赖加入 sourceSets
// 这样测试代码可以直接 import 生产代码，同时构建完全独立
val appMainSrc = file("../app/src/main/java")
val appTestSrc = file("../app/src/test/java")

sourceSets {
    test {
        java {
            srcDir(appMainSrc)   // 引入生产代码源码
            srcDir(appTestSrc)   // 引入旧测试代码（迁移期共存）
        }
        // 编译时依赖：Android SDK stub（仅编译期，不打包）
        compileClasspath += files(
            // Android SDK 类路径（由环境变量 ANDROID_HOME 指定）
            // 实际构建时由 run-tests.sh 注入
        )
    }
}

// ===== 测试依赖声明 =====
dependencies {
    // 测试框架
    testImplementation("junit:junit:4.13.2")

    // Mock 框架（替代 Android 依赖）
    testImplementation("io.mockk:mockk:1.13.12")

    // 协程测试支持
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // 断言增强（可选，Kotlin 友好）
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")

    // Room 编译期 stub（仅用于编译通过，测试中不实际使用 Room）
    // 测试使用 Fake DAO 替代，见 fixtures/FakeDaos.kt
    compileOnly("androidx.room:room-common:2.6.1")
    compileOnly("androidx.room:room-ktx:2.6.1")

    // Kotlin 标准库
    implementation(kotlin("stdlib"))
}

// ===== 测试任务配置 =====
tasks.test {
    useJUnitPlatform()  // 如果迁移到 JUnit 5
    // 或：
    // useJUnit()        // 当前 JUnit 4 模式

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    // 失败即停（CI 友好）
    ignoreFailures = false
}

// ===== 独立运行支持 =====
// 注册一个独立任务，不依赖根项目
tasks.register("runTests") {
    group = "verification"
    description = "Run all unit tests independently"
    dependsOn(tasks.test)
}
```

### 4.2 依赖版本集中管理

`testing/config/test-dependencies.toml`（使用 Gradle Version Catalog 格式）：

```toml
[versions]
junit = "4.13.2"
mockk = "1.13.12"
kotlinx-coroutines-test = "1.8.1"
kotest-assertions = "5.9.1"
room = "2.6.1"

[libraries]
junit = { module = "junit:junit", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines-test" }
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest-assertions" }
room-common = { module = "androidx.room:room-common", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
```

### 4.3 依赖隔离原则

| 依赖类型 | 来源 | 说明 |
|----------|------|------|
| `testImplementation` | 独立声明 | 测试框架、Mock、断言库，与主项目完全解耦 |
| `compileOnly` | 独立声明 | Android/Room API stub，仅编译期存在 |
| 生产代码 | `sourceSets` 引入 | 通过源码路径引用，不打成 jar，不产生二进制依赖 |
| 禁止引入 | — | `androidx.appcompat`、`androidx.lifecycle` 等运行时 Android 依赖 |

---

## 五、测试执行

### 5.1 独立执行脚本 `testing/run-tests.sh`

```bash
#!/usr/bin/env bash
# ============================================================
# 简记 单元测试独立执行脚本
# 用法：
#   ./run-tests.sh              # 运行全部单元测试
#   ./run-tests.sh --filter *ViewModelTest  # 按名称过滤
#   ./run-tests.sh --category P0           # 按优先级分类
#   ./run-tests.sh --watch                 # 持续监听模式
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 确保 ANDROID_HOME 已设置（编译期需要 Android SDK）
if [ -z "${ANDROID_HOME:-}" ]; then
    # 尝试常见路径
    for candidate in "$HOME/Android/Sdk" "/usr/local/android-sdk" "/opt/android-sdk"; do
        if [ -d "$candidate" ]; then
            export ANDROID_HOME="$candidate"
            break
        fi
    done
fi

# 参数解析
FILTER=""
CATEGORY=""
WATCH_MODE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --filter) FILTER="$2"; shift 2 ;;
        --category) CATEGORY="$2"; shift 2 ;;
        --watch) WATCH_MODE=true; shift ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# 构建 Gradle 参数
GRADLE_ARGS=":testing:test"

if [ -n "$FILTER" ]; then
    GRADLE_ARGS="$GRADLE_ARGS --tests \"$FILTER\""
fi

# 如果指定了 category，通过 JUnit Category 过滤
if [ -n "$CATEGORY" ]; then
    GRADLE_ARGS="$GRADLE_ARGS -PtestCategory=$CATEGORY"
fi

# 执行（从根目录跑，因为 testing 模块在根 settings.gradle.kts 中 include）
echo -e "${YELLOW}>>> 运行单元测试...${NC}"

cd "$(git rev-parse --show-toplevel 2>/dev/null || echo "$SCRIPT_DIR/..")"

if $WATCH_MODE; then
    # 文件变更时自动重跑（需要安装 fswatch 或 inotify-tools）
    echo -e "${YELLOW}>>> 监听模式：文件变更时自动重跑${NC}"
    while true; do
        inotifywait -r -e modify,create,delete \
            testing/src/test/ app/src/main/java/ 2>/dev/null
        ./gradlew $GRADLE_ARGS
    done
else
    ./gradlew $GRADLE_ARGS
    EXIT_CODE=$?

    if [ $EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}>>> 全部测试通过${NC}"
    else
        echo -e "${RED}>>> 测试失败 (exit=$EXIT_CODE)${NC}"
    fi
    exit $EXIT_CODE
fi
```

### 5.2 根项目 `settings.gradle.kts` 修改

```kotlin
// 新增一行
include(":testing")
```

### 5.3 常见执行命令

| 命令 | 用途 |
|------|------|
| `./testing/run-tests.sh` | 运行全部单元测试 |
| `./testing/run-tests.sh --filter "*ViewModelTest"` | 只跑 ViewModel 测试 |
| `./gradlew :testing:test --tests "*.BudgetTest"` | 只跑预算相关测试 |
| `./gradlew :testing:test -PtestCategory=P0` | 只跑 P0 优先级测试 |
| `./testing/run-tests.sh --watch` | 开发时持续监听 |

---

## 六、迁移计划

### 6.1 迁移步骤

```
Phase 1：创建 testing 模块骨架（0.5 天）
  ├── 创建 testing/ 目录及 build.gradle.kts
  ├── 配置 sourceSets 引用 app/src/main/java
  ├── 在根 settings.gradle.kts 中 include(":testing")
  ├── 创建 run-tests.sh
  └── 验证：./gradlew :testing:test 能跑通（即使无测试）

Phase 2：迁移现有测试文件（0.5 天）
  ├── 将 app/src/test/ 下 4 个文件复制到 testing/src/test/java/ 对应目录
  ├── 修复编译错误（Android API stub 问题）
  ├── 验证：./testing/run-tests.sh 全部通过
  └── 删除 app/src/test/ 下的原文件

Phase 3：补充 P0 测试（按 P0 方案执行，1.5 天）
  ├── testing/src/test/java/.../viewmodel/TransactionViewModelTest.kt
  ├── testing/src/test/java/.../viewmodel/BudgetViewModelTest.kt
  └── 验证：5 个核心路径 + 3 个预算路径全部通过

Phase 4：CI 适配（0.5 天）
  ├── 修改 .github/workflows/build-apk.yml
  │   └── 将 ./gradlew testDebugUnitTest 改为 ./testing/run-tests.sh
  ├── 仪器化测试（app/src/androidTest）保持原路径不变
  └── 验证：CI 绿色

Phase 5：清理与文档（0.5 天）
  ├── 删除 app/build.gradle.kts 中迁移走的 testImplementation 依赖
  ├── 更新 README.md 中的测试运行说明
  └── 归档本方案
```

### 6.2 迁移期间的兼容策略

- **共存期**（Phase 2）：`testing/src/test/` 和 `app/src/test/` 同时存在，两边都跑
- **切换点**（Phase 2 末尾）：确认 `testing/` 通过后删除 `app/src/test/`
- **回滚**：如果 `testing/` 出问题，恢复 `app/src/test/` 即可，`testing/` 目录可随时删除

### 6.3 不清除的内容

| 保留项 | 位置 | 原因 |
|--------|------|------|
| 仪器化测试 | `app/src/androidTest/` | 依赖 Android 框架，无法分离 |
| Room schema JSON | `app/schemas/` | androidTest 的 MigrationTestHelper 需要 |
| `debugImplementation` 依赖 | `app/build.gradle.kts` | Compose UI 测试需要 |

---

## 七、风险控制

| 风险 | 控制措施 |
|------|---------|
| Android SDK 类编译失败 | `compileOnly` 引入 Room stub + `android.jar`；测试中使用 Fake DAO 替代真实 Room |
| `sourceSets` 引入源码导致类冲突 | 使用 `srcDir()` 而非 `compileClasspath` 方式，确保只有测试编译期可见 |
| CI 中 ANDROID_HOME 不可用 | run-tests.sh 内置多路径探测；若 CI 无 SDK，改为仅运行纯 Kotlin 测试（不依赖 android.* 的测试） |
| 根 settings.gradle.kts 修改破坏现有构建 | `include(":testing")` 仅新增一个子项目，不影响现有 `:app` |
| 两份 build.gradle.kts 依赖版本不同步 | Version Catalog 集中管理（`test-dependencies.toml`），单一事实来源 |
| MockK 与现有 JUnit 4 测试不兼容 | MockK 兼容 JUnit 4，通过 `mockk()` 和 `every{}` 使用，无需迁移到 JUnit 5 |

---

## 八、目录结构全景图

```
root/
├── app/                                    # 主项目（Android Application）
│   ├── src/main/java/com/example/jianji/   # 生产代码
│   │   ├── data/                           #   - 实体、DAO、Repository
│   │   ├── ui/viewmodel/                   #   - ViewModel
│   │   ├── ui/screens/                     #   - Composable UI
│   │   └── utils/                          #   - 工具类
│   ├── src/androidTest/                    # 仪器化测试（保留）
│   ├── schemas/                            # Room schema JSON（保留）
│   └── build.gradle.kts
├── testing/                                # ★ 独立单元测试项目
│   ├── build.gradle.kts                    # 独立构建配置
│   ├── run-tests.sh                        # 独立执行脚本
│   └── src/test/java/com/example/jianji/
│       ├── data/                           # 数据层测试
│       │   ├── BudgetDaoTest.kt
│       │   ├── TransactionDaoTest.kt
│       │   ├── TransactionRepositoryTest.kt
│       │   ├── BudgetRepositoryTest.kt
│       │   ├── CategoryRepositoryTest.kt
│       │   ├── AccountRepositoryTest.kt
│       │   ├── TagRepositoryTest.kt
│       │   ├── RecurringTransactionRepositoryTest.kt
│       │   └── ConvertersTest.kt
│       ├── viewmodel/                      # ViewModel 层测试
│       │   ├── TransactionViewModelTest.kt
│       │   ├── BudgetViewModelTest.kt
│       │   ├── AccountViewModelTest.kt
│       │   ├── CategoryViewModelTest.kt
│       │   ├── SettingsViewModelTest.kt
│       │   └── TagViewModelTest.kt
│       ├── utils/                          # 工具类测试
│       │   ├── RecurringScheduleTest.kt    # [迁移]
│       │   ├── StatisticsCalculatorTest.kt # [迁移]
│       │   ├── VersionComparatorTest.kt    # [迁移]
│       │   ├── ImportParsingToleranceTest.kt # [迁移]
│       │   ├── DateUtilsTest.kt
│       │   ├── BackupStorageTest.kt
│       │   └── AppPrefsTest.kt
│       └── fixtures/                       # 共享测试夹具
│           ├── TestDataFactory.kt
│           ├── FakeDaos.kt
│           └── CoroutineTestRules.kt
├── docs/
│   ├── unit-test-separation-plan.md        # 本方案
│   └── P0_P1_P3_优化方案.md
├── settings.gradle.kts                     # 根 settings（+ include(":testing")）
└── build.gradle.kts
```

---

## 九、与 P0/P1/P3 方案的衔接

| P0/P1/P3 项 | 原方案位置 | 独立化后位置 | 说明 |
|-------------|-----------|-------------|------|
| P0-1 TransactionViewModelTest | `app/src/test/.../viewmodel/` | `testing/src/test/.../viewmodel/` | 新建文件 |
| P0-2 Room MigrationTest | `app/src/androidTest/.../data/` | **不变**（仪器化测试） | 保留在 androidTest |
| P0-3 BudgetViewModelTest | `app/src/test/.../viewmodel/` | `testing/src/test/.../viewmodel/` | 新建文件 |
| P0-1 MockK 依赖 | `app/build.gradle.kts` | `testing/build.gradle.kts` | 依赖声明移入 testing |
| P3-2 移除 kotlinx-serialization | `app/build.gradle.kts` | 不变 | 与测试分离无关 |

**实施顺序调整**：

```
第 1 天：Phase 1-2（创建 testing 模块 + 迁移现有测试）
第 2 天：Phase 3（P0-1 + P0-3 测试编写，在 testing 模块中）
第 3 天：P0-2（仪器化测试，在 androidTest 中）+ Phase 4（CI 适配）
第 4 天：P1-1 + P1-2（生产代码改动，测试在 testing 中同步更新）
第 5 天：P3-1 + P3-2 + Phase 5（清理 + 文档）
```

---

## 十、验收标准

| 标准 | 验证方式 |
|------|---------|
| `./testing/run-tests.sh` 可独立运行，不依赖 `:app` 的 Gradle task | 删除 `app/build/` 后执行脚本，测试仍通过 |
| 全部现有测试（4 个 utils 测试）迁移后通过 | `./testing/run-tests.sh` 输出 4/4 passed |
| 主项目 `./gradlew :app:assembleDebug` 不受影响 | 构建通过，APK 生成正常 |
| CI 单元测试 job 切换到 `./testing/run-tests.sh` | CI 绿色 |
| 新增测试文件均在 `testing/` 下，不在 `app/src/test/` | `find app/src/test -name "*.kt"` 返回空（或仅剩旧文件） |