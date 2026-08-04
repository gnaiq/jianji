# This is a configuration file for ProGuard.
# http://proguard.sourceforge.net/index.html#manual/usage.html

-dontusemixedcaseclassnames
-verbose

# Preserve line numbers for debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep classes that are referenced by the Android framework.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Kotlin metadata
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# Compose: 无需手写 -keep，AGP 会应用 compose 库自带的 consumer ProGuard 规则。
# 原先全量 keep androidx.compose.** 会让代码收缩几乎失效，已移除。

# Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
# Gson 库自带 consumer rules（META-INF/proguard/gson.pro）已覆盖其内部类，故移除 -keep com.google.gson.**。
# 业务序列化 DTO 必须保留：备份/恢复的 ImportData 及 *Import 系列 DTO 全部位于 utils 包，
# Gson 依赖「字段名反射 + 字段泛型签名（List<TransactionImport> 等）」做反序列化。
# 若这些类被 R8 混淆/裁剪，嵌套 List 的元素类型在运行时会绑定到错误类，
# 触发 gson 内部 checkcast 失败：典型报错 "p6.n cannot be cast to z5.h"（仅 release 混淆包出现）。
# 因此 data.** 与 utils.** 两类 DTO 都必须 -keep 且保留字段名。
# 修复 P6-2 加固：显式点名备份序列化 DTO 与加密工具类，防止未来把 DTO 移出 utils 包后
# 丢失 -keep 导致 release 包恢复备份崩溃。
-keep class com.example.jianji.data.** { *; }
-keep class com.example.jianji.utils.** { *; }
-keep class com.example.jianji.utils.ImportData { *; }
-keep class com.example.jianji.utils.*Import { *; }
-keep class com.example.jianji.utils.BackupCrypto { *; }

# Keep Timber (for tag-based filtering)
-dontwarn timber.log.**
-keep class timber.log.** { *; }

# Keep kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.jianji.**$$serializer { *; }
-keepclassmembers class com.example.jianji.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.jianji.** {
    kotlinx.serialization.KSerializer serializer(...);
}
