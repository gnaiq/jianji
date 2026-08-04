plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.jianji"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.jianji"
        minSdk = 24
        targetSdk = 34
        versionCode = 61
        versionName = "1.6.28"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotEmpty() } ?: "release.keystore")
            storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
        // 关键修复：CI 环境下让 debug 与 release 使用同一份密钥，
        // 使本地用 Android Studio 安装的调试包与 GitHub 发布的正式包“同签名”，
        // 避免覆盖安装时因签名不一致失败；同时让“同版本号重装”也能通过。
        // 本地未配置 KEYSTORE_FILE 时保留默认 debug 密钥，不影响日常开发。
        getByName("debug") {
            if (!System.getenv("KEYSTORE_FILE").isNullOrEmpty()) {
                storeFile = file(System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotEmpty() } ?: "release.keystore")
                storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 有 CI 签名密钥时用固定 release keystore，否则回退 debug（本地开发）
            signingConfig = if (!System.getenv("KEYSTORE_FILE").isNullOrEmpty())
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk=24 但项目大量使用 java.time.*（API 26+）；启用脱糖使 Android 7.x 可运行
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/README",
                "/META-INF/INDEX.LIST",
                "/META-INF/versions/9/module-info.class"
            )
        }
    }

    // Room 导出的 schema JSON 目录随 androidTest assets 打包，供 MigrationTestHelper 读取
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

// Room 编译期把每个 version 的 schema 导出到 app/schemas/（配合 JianjiDatabase exportSchema=true）
// 首次由 CI 构建生成，生成后需将 schemas/ 提交入库，作为迁移测试基线（详见 docs/migration-testing.md）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // java.time 脱糖（配合 isCoreLibraryDesugaringEnabled，支撑 minSdk 24）
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-graphics:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.material:material:1.6.8")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Charts (for statistics)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // CSV Export
    implementation("org.apache.commons:commons-csv:1.10.0")

    // JSON parsing for import
    implementation("com.google.code.gson:gson:2.10.1")

    // Glance AppWidget
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // WorkManager（周期自动备份：调度持久化、跨重启恢复、Doze 维护窗口内执行）
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Logging (Timber - 结构化日志，替代静默 catch 和崩溃日志写文件)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Koin DI (轻量依赖注入，替代手动实例化)
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("io.insert-koin:koin-androidx-compose:3.5.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // Room 迁移测试（MigrationTestHelper），配合导出的 schema JSON 验证升级
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
}