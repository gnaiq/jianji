package com.example.jianji.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

class UpdateManager(private val context: Context) {

    data class ReleaseInfo(
        val versionName: String,
        val downloadUrl: String,
        val body: String,
        val apkSize: Long
    )

    companion object {
        private const val GITHUB_API = "https://api.github.com/repos/gnaiq/jianji/releases/latest"
    }

    /**
     * 检查 GitHub 最新 Release。
     * 返回 null 表示当前已是最新版。
     */
    suspend fun checkForUpdate(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(GITHUB_API).openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "jianji-android")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (connection.responseCode != 200) {
                return@withContext Result.failure(Exception("GitHub API error: ${connection.responseCode}"))
            }

            val json = JSONObject(body)
            val tagName = json.getString("tag_name")
            // tagName 格式: v1.2.0
            val releaseName = json.optString("name", tagName)
            val releaseBody = json.optString("body", "")

            val assets = json.getJSONArray("assets")
            var downloadUrl = ""
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url")
                    apkSize = asset.getLong("size")
                    break
                }
            }

            if (downloadUrl.isEmpty()) {
                return@withContext Result.failure(Exception("未在 Release 中找到 APK 下载链接"))
            }

            // 用版本号字符串做语义化比较，避免 versionCode 映射不一致
            val latestVersion = tagName.removePrefix("v").trim()
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
            } catch (_: Exception) { "0" }

            if (!isNewerVersion(currentVersion, latestVersion)) {
                Result.success(null) // 已是最新版
            } else {
                Result.success(ReleaseInfo(latestVersion, downloadUrl, releaseBody, apkSize))
            }
        } catch (e: Exception) {
            val msg = when (e) {
                is java.net.UnknownHostException -> "无法连接更新服务器（网络受限或被拦截）"
                is java.net.SocketTimeoutException -> "连接更新服务器超时"
                else -> e.message ?: "未知错误"
            }
            Result.failure(Exception(msg))
        }
    }

    /**
     * 语义化比较版本号：latest 是否比 current 更新。
     * 例：current="1.3.0", latest="1.3.5" -> true；current="1.4.0", latest="1.3.5" -> false
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(c.size, l.size)
        for (i in 0 until len) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    /**
     * 通过 DownloadManager 下载 APK，下载完成后自动触发安装。
     * 返回下载 ID，可用于查询进度。
     */
    fun downloadAndInstall(url: String): Long {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "jianji_update.apk")
        if (apkFile.exists()) apkFile.delete()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("简记更新")
            setDescription("正在下载新版本...")
            setDestinationUri(Uri.fromFile(apkFile))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // 注册广播监听下载完成
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return

                context.unregisterReceiver(this)

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    cursor.close()
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        installApk(apkFile)
                    } else {
                        Toast.makeText(context, "下载失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        return downloadId
    }

    /** 本机是否已存在此前下载好的**真正新于当前版本**的安装包（防止上次更新残留的同级/旧包被误判） */
    fun hasLocalApk(): Boolean {
        val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "jianji_update.apk")
        if (!f.exists() || f.length() == 0L) return false
        val archiveInfo = context.packageManager.getPackageArchiveInfo(f.absolutePath, 0) ?: return false
        val apkVc = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (apkVc <= 0) return false
        val installedVc = try {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
        } catch (_: Exception) { 0L }
        return apkVc > installedVc
    }

    /** 安装本机已下载好的更新安装包（检查更新失败但仍已下好包时复用） */
    fun installLocalApk() {
        val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "jianji_update.apk")
        if (f.exists()) installApk(f)
        else Toast.makeText(context, "未找到本地安装包", Toast.LENGTH_SHORT).show()
    }

    /** 手动下载地址 */
    fun releasesUrl(): String = "https://github.com/gnaiq/jianji/releases"

    /**
     * 使用 FileProvider 安装 APK
     */
    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "已下载更新，请点击通知或到下载目录手动安装",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
