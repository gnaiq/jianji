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
        val versionCode: Int,
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
                return@withContext Result.failure(Exception("未找到 APK 下载链接"))
            }

            // 从 tag 解析 versionCode：用 versionName 推一个保守估计
            // GitHub Tag 里没有 versionCode，用 CI 一致的映射: major*1000 + minor*100 + patch
            val versionName = tagName.removePrefix("v")
            val parts = versionName.split(".").map { it.toIntOrNull() ?: 0 }
            val versionCode = (parts.getOrElse(0) { 0 }) * 1000 +
                              (parts.getOrElse(1) { 0 }) * 100 +
                              (parts.getOrElse(2) { 0 })

            val currentCode = context.packageManager
                .getPackageInfo(context.packageName, 0).versionCode

            if (versionCode <= currentCode) {
                Result.success(null) // 已是最新版
            } else {
                Result.success(ReleaseInfo(versionName, versionCode, downloadUrl, releaseBody, apkSize))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        context.startActivity(intent)
    }
}
