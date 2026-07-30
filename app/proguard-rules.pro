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
# 但业务数据类仍需保留：Room 实体 + Gson/序列化经由 utils 的 DTO 依赖字段名反射，
# 收窄该规则需 CI 打包运行验证备份/导入/导出无误后再进行，本次保守保留。
-keep class com.example.jianji.data.** { *; }

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
