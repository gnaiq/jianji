package com.example.jianji

import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.jianji.ui.JianjiApp
import com.example.jianji.ui.theme.JianjiTheme
import com.example.jianji.core.common.AppPrefs
import com.example.jianji.core.update.UpdateManager
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 诊断：若上次启动已崩溃并记录，优先用系统弹窗展示异常栈，便于无 adb 反馈根因
        val crashFile = File(getExternalFilesDir(null) ?: filesDir, "crash_log.txt")
        if (crashFile.exists()) {
            val text = crashFile.readText().take(6000)
            android.app.AlertDialog.Builder(this)
                .setTitle("上次启动崩溃信息（请截图或点“复制”反馈）")
                .setMessage(text)
                .setPositiveButton("复制") { _, _ ->
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("jianji_crash", text))
                }
                .setNegativeButton("关闭") { _, _ -> crashFile.delete() }
                .show()
        }
        // 更新后清理安装包与缓存：安装完成时会标记，下次冷启动在此执行真正的清除
        try {
            val up = getSharedPreferences("jianji_update", MODE_PRIVATE)
            if (up.getBoolean("pending_clear", false)) {
                up.edit().putBoolean("pending_clear", false).apply()
                UpdateManager(this).clearUpdateCache()
            }
        } catch (e: Exception) {
            Timber.w(e, "启动时清理更新缓存失败")
        }

        setContent {
            val ctx = this
            var darkMode by remember { mutableIntStateOf(AppPrefs.getDarkMode(ctx)) }
            JianjiTheme(darkTheme = darkMode == 2 || (darkMode == 0 && isSystemInDarkTheme())) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JianjiApp(
                        darkMode = darkMode,
                        onDarkModeChange = { mode ->
                            darkMode = mode
                            AppPrefs.setDarkMode(ctx, mode)
                        }
                    )
                }
            }
        }
    }
}
