package com.example.jianji.utils

/**
 * 语义化比较版本号：latest 是否比 current 更新。
 * 例：current="1.3.0", latest="1.3.5" -> true；current="1.4.0", latest="1.3.5" -> false
 *
 * 注意：版本号段可能带非数字后缀（如 "1.4.22-fixed"），解析时只取前导数字，
 * 避免 "22-fixed" 被 toIntOrNull 解析成 0 而误判（修复 P2-2d）。
 */
fun compareVersionNewer(current: String, latest: String): Boolean {
    val c = current.split(".").map { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
    val l = latest.split(".").map { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
    val len = maxOf(c.size, l.size)
    for (i in 0 until len) {
        val cv = c.getOrElse(i) { 0 }
        val lv = l.getOrElse(i) { 0 }
        if (lv > cv) return true
        if (lv < cv) return false
    }
    return false
}
