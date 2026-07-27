package com.example.jianji.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 自动备份调度：基于 AlarmManager 的周期性（不精确）闹钟，零额外依赖。
 * 默认每周执行一次，支持配置：每天 / 每周 / 每月 / 关闭。
 */
object BackupScheduler {
    private const val PREFS = "jianji_backup_prefs"
    const val KEY_INTERVAL_DAYS = "auto_backup_interval_days"
    const val DEFAULT_DAYS = 7
    const val ACTION_AUTO_BACKUP = "com.example.jianji.ACTION_AUTO_BACKUP"
    const val KEY_IMMEDIATE_BACKUP = "immediate_backup_enabled"

    fun getIntervalDays(context: Context): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (!p.contains(KEY_INTERVAL_DAYS)) DEFAULT_DAYS else p.getInt(KEY_INTERVAL_DAYS, DEFAULT_DAYS)
    }

    /** 数据变更时是否即时备份（默认开启，保持原有行为） */
    fun isImmediateBackupEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IMMEDIATE_BACKUP, true)
    }

    fun setImmediateBackupEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IMMEDIATE_BACKUP, enabled).apply()
    }

    fun saveIntervalDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INTERVAL_DAYS, days).apply()
        apply(context, days)
    }

    /** 应用并设置闹钟；days<=0 表示关闭 */
    fun apply(context: Context, days: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        am.cancel(pi)
        if (days <= 0) return
        val interval = AlarmManager.INTERVAL_DAY * days
        val triggerAt = System.currentTimeMillis() + interval
        @Suppress("DEPRECATION")
        am.setInexactRepeating(AlarmManager.RTC, triggerAt, interval, pi)
    }

    /** 应用启动时调用：仅当已启用（days>0）时确保已登记闹钟 */
    fun ensureScheduled(context: Context) {
        val days = getIntervalDays(context)
        if (days > 0) apply(context, days)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutoBackupReceiver::class.java).apply {
            action = ACTION_AUTO_BACKUP
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}