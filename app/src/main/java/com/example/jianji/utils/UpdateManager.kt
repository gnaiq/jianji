package com.example.jianji.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
            connection.setRequestProperty("Cache-Control", "no-cache, no-store")
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
     * 通过 HttpURLConnection 直接下载 APK（绕过 DownloadManager 的 file:// URI 暴露问题），
     * 下载完成后用 FileProvider 触发安装。下载与检查更新走同一网络通路。
     */
    suspend fun downloadAndInstall(url: String) = withContext(Dispatchers.IO) {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "jianji_update.apk")
        if (apkFile.exists()) apkFile.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "jianji-android")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("下载服务器返回错误码 ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        if (apkFile.length() == 0L) {
            throw Exception("下载内容为空，可能网络被拦截")
        }

        val blocked = installBlockedReason(apkFile)
        if (blocked != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
            }
            return@withContext
        }

        withContext(Dispatchers.Main) { installApk(apkFile) }
    }

    /**
     * 安装前自检：读取下载 APK 的包名 / versionCode / 签名，与已装应用比对。
     * 返回 null 表示可以安装；否则返回需要提示给用户的原因。
     * 作用：把系统含糊的“已安装更高版本 / 应用未安装”转成明确、可执行的提示，
     * 避免发起注定失败的覆盖安装（Android 禁止 versionCode 降级，也不同签名覆盖）。
     */
    private fun installBlockedReason(apk: File): String? {
        val pm = context.packageManager
        val archiveFlags = PackageManager.GET_META_DATA or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, archiveFlags)
            ?: return "无法读取安装包信息，请到下载目录手动安装"

        if (archive.packageName != context.packageName) {
            return "安装包包名(${archive.packageName})与当前应用不一致，无法覆盖安装"
        }

        val installedFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else 0
        val installed = try {
            pm.getPackageInfo(context.packageName, installedFlags)
        } catch (_: Exception) { null }

        if (installed != null) {
            // 1) 签名一致性：设备上若是调试/本地构建，与发布版签名不同，系统禁止覆盖安装
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val same = certSet(installed.signingInfo) == certSet(archive.signingInfo)
                if (!same) {
                    return "下载的安装包与已安装应用签名不一致（设备上装的可能是调试/本地构建版）。\n" +
                            "Android 不允许不同签名的覆盖安装。请先卸载当前应用，再到 GitHub 安装正式版。"
                }
            }
            // 2) 版本降级/同版本：versionCode 必须严格递增，且以成功读取到的为准（apkVc>0 才参与判断）
            val installedVc = PackageInfoCompat.getLongVersionCode(installed)
            val apkVc = PackageInfoCompat.getLongVersionCode(archive)
            if (apkVc > 0 && apkVc <= installedVc) {
                val reason = if (apkVc < installedVc) "低于" else "不高于（同版本）"
                return "下载的安装包版本(code $apkVc) $reason 已安装版本(code $installedVc)，系统禁止降级或同版本覆盖安装。\n" +
                        "可能是设备上装的是本地测试版（versionCode 更高），或上次更新的安装包残留。请先卸载当前应用，再安装正式版。"
            }
        }
        return null
    }

    /** 提取签名证书 SHA-256 集合，用于判断两个 APK 是否同源签名 */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun certSet(info: SigningInfo?): Set<String> {
        if (info == null) return emptySet()
        val certs = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return certs.map { md.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
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
        if (!f.exists()) {
            Toast.makeText(context, "未找到本地安装包", Toast.LENGTH_SHORT).show()
            return
        }
        // 自检：本地 APK 版本是否真正新于已装版本（防止上次更新残留的同级/旧包被误装）
        val archiveInfo = context.packageManager.getPackageArchiveInfo(f.absolutePath, 0)
        if (archiveInfo != null) {
            val apkVc = PackageInfoCompat.getLongVersionCode(archiveInfo)
            val installedVc = try {
                PackageInfoCompat.getLongVersionCode(
                    context.packageManager.getPackageInfo(context.packageName, 0)
                )
            } catch (_: Exception) { 0L }
            if (apkVc > 0 && apkVc <= installedVc) {
                f.delete()
                Toast.makeText(context,
                    "本地安装包版本不新于当前已装版本，已自动清理残留文件",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        val reason = installBlockedReason(f)
        if (reason != null) {
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            return
        }
        installApk(f)
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
