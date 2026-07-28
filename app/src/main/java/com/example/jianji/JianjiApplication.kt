package com.example.jianji

import android.app.Application
import android.os.Build
import com.example.jianji.utils.BackupScheduler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class JianjiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
            def?.uncaughtException(thread, throwable)
        }
    }
}
