package com.example.jianji.core.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 自动备份调度：基于 WorkManager 的周期任务（调度状态由 WorkManager 持久化，
 * 跨进程/设备重启自动恢复，无需 BOOT_COMPLETED 广播）。
 * 默认每周执行一次，支持配置：每天 / 每周 / 每月 / 关闭。
 *
 * 关键语义：
 * - ensureScheduled（App 启动时调用）使用 KEEP 策略——已在计时的周期不会被重置，
 *   修复了旧 AlarmManager 实现「每次启动重置倒计时导致定时备份永不触发」的缺陷。
 * - apply（用户变更设置时调用）使用 UPDATE 策略——立即按新周期生效。
 */
object BackupScheduler {
    private const val PREFS = "jianji_backup_prefs"
    const val KEY_INTERVAL_DAYS = "auto_backup_interval_days"
    const val DEFAULT_DAYS = 7
    const val KEY_IMMEDIATE_BACKUP = "immediate_backup_enabled"
    const val KEY_LAST_BACKUP_AT = "auto_backup_last_at"
    private const val WORK_NAME = "auto_backup"

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

    /** 上次自动备份完成时间（毫秒），0 表示尚未执行过 */
    fun getLastBackupAt(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_BACKUP_AT, 0L)
    }

    fun setLastBackupAt(context: Context, at: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_BACKUP_AT, at).apply()
    }

    fun saveIntervalDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INTERVAL_DAYS, days).apply()
        apply(context, days)
    }

    /** 用户变更周期时调用：UPDATE 立即按新周期重排；days<=0 表示关闭 */
    fun apply(context: Context, days: Int) {
        val wm = WorkManager.getInstance(context)
        if (days <= 0) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request(days))
    }

    /** 应用启动时调用：KEEP 策略，仅在未登记时排期，不重置已在计时的周期 */
    fun ensureScheduled(context: Context) {
        val days = getIntervalDays(context)
        if (days <= 0) return
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request(days))
    }

    private fun request(days: Int) =
        PeriodicWorkRequestBuilder<AutoBackupWorker>(days.toLong(), TimeUnit.DAYS).build()
}
