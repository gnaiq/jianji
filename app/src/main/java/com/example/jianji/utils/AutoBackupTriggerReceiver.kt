package com.example.jianji.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 执行一次数据库自动备份。仅由本应用 BackupScheduler 的显式 PendingIntent 触发
 * （AndroidManifest 中 exported=false），任意第三方 App 无法借 ACTION_AUTO_BACKUP
 * 广播触发备份写盘（P1-4 暴露面收敛）。
 * 使用 goAsync() 持有广播唤醒锁直到备份协程完成，避免进程被提前回收导致备份中断。
 */
class AutoBackupTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BackupScheduler.ACTION_AUTO_BACKUP) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AutoBackup.run(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
