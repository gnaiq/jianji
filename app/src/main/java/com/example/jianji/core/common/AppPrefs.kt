package com.example.jianji.core.common

import android.content.Context

/** 应用级轻量偏好存储（深色模式等） */
object AppPrefs {
    private const val NAME = "jianji_prefs"
    private const val KEY_DARK = "dark_mode" // 0=跟随系统 1=浅色 2=深色
    private const val KEY_BACKUP_PASS = "backup_passphrase" // 备份加密口令（P6-1）；空=不加密

    fun getDarkMode(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_DARK, 0)

    fun setDarkMode(context: Context, mode: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DARK, mode).apply()
    }

    /** 备份加密口令（P6-1）；空字符串表示未启用加密 */
    fun getBackupPassphrase(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_BACKUP_PASS, "") ?: ""

    fun setBackupPassphrase(context: Context, passphrase: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(KEY_BACKUP_PASS, passphrase).apply()
    }
}
