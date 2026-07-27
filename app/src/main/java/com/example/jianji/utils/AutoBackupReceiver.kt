package com.example.jianji.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接收定时自动备份闹钟与设备开机广播：
 *  - ACTION_AUTO_BACKUP：执行一次数据库自动备份
 *  - BOOT_COMPLETED：重新登记周期闹钟（闹钟在重启后会失效）
 *
 * 使用 goAsync() 持有广播唤醒锁，直到协程内备份完成才释放，
 * 避免系统在主线程 onReceive 返回后过早回收进程导致备份中断（P2-4）。
 */
class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                try {
                    BackupScheduler.ensureScheduled(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
            BackupScheduler.ACTION_AUTO_BACKUP -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AutoBackup.run(context.applicationContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> pendingResult.finish()
        }
    }
}
