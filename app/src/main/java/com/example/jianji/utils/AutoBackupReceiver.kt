package com.example.jianji.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 仅在设备开机完成后重新登记周期备份闹钟（闹钟重启后会失效）。
 * 自动备份的实际执行已拆分到 AutoBackupTriggerReceiver（exported=false），
 * 仅由本应用 BackupScheduler 的显式 PendingIntent 触发，避免任意第三方 App
 * 借自定义 action 反复触发全量备份写盘（P1-4 暴露面收敛）。
 */
class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                BackupScheduler.ensureScheduled(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        } else {
            pendingResult.finish()
        }
    }
}
