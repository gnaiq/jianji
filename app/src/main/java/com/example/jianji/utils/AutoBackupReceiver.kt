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
 */
class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                BackupScheduler.ensureScheduled(context.applicationContext)
            }
            BackupScheduler.ACTION_AUTO_BACKUP -> {
                CoroutineScope(Dispatchers.IO).launch {
                    AutoBackup.run(context.applicationContext)
                }
            }
        }
    }
}