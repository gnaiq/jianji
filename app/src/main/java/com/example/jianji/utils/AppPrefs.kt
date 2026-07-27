package com.example.jianji.utils

import android.content.Context

/** 应用级轻量偏好存储（深色模式等） */
object AppPrefs {
    private const val NAME = "jianji_prefs"
    private const val KEY_DARK = "dark_mode" // 0=跟随系统 1=浅色 2=深色

    fun getDarkMode(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_DARK, 0)

    fun setDarkMode(context: Context, mode: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DARK, mode).apply()
    }
}
