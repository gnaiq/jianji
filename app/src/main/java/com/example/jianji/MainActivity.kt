package com.example.jianji

import android.os.Bundle
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
import com.example.jianji.utils.AppPrefs
import com.example.jianji.utils.UpdateManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 更新后清理安装包与缓存：安装完成时会标记，下次冷启动在此执行真正的清除
        try {
            val up = getSharedPreferences("jianji_update", MODE_PRIVATE)
            if (up.getBoolean("pending_clear", false)) {
                up.edit().putBoolean("pending_clear", false).apply()
                UpdateManager(this).clearUpdateCache()
            }
        } catch (_: Exception) { }

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
