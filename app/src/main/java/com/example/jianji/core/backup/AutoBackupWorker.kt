package com.example.jianji.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 周期自动备份 Worker：由 BackupScheduler 以 PeriodicWorkRequest 调度。
 * AutoBackup.run 内部已捕获异常并记录日志，此处恒返回 success，
 * 避免「无数据可备份」被误判为失败而进入重试。
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AutoBackup.run(applicationContext)
        return Result.success()
    }
}
