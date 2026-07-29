package com.example.jianji

import android.app.Application
import android.os.Build
import com.example.jianji.utils.BackupScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class JianjiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Koin DI 初始化（必须在其他组件之前）
        startKoin {
            androidContext(this@JianjiApplication)
            modules(appModule)
        }
        // 结构化日志：Debug 环境输出到 logcat，Release 环境静默（不写文件，避免泄漏敏感数据）
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // 诊断优先：先装崩溃捕获器，确保任何后续启动异常都能落盘，便于无 adb 环境下定位根因
        installCrashHandler()
        BackupScheduler.ensureScheduled(this)
    }

    /** 诊断用：捕获未处理异常并写入 crash_log.txt，供下次启动弹窗展示 */
    private fun installCrashHandler() {
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val dir = getExternalFilesDir(null) ?: filesDir
                File(dir, "crash_log.txt").writeText(
                    "time=${System.currentTimeMillis()} sdk=${Build.VERSION.SDK_INT}\n" +
                    "${throwable.javaClass.name}: ${throwable.message}\n${sw}\n"
                )
            }
            // 使用 Timber 记录崩溃（如果已初始化）
            Timber.e(throwable, "Uncaught exception in thread ${thread.name}")
            def?.uncaughtException(thread, throwable)
        }
    }
}