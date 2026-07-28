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
import java.util.concurrent.atomic.AtomicBoolean

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

    private val updatePrefs by lazy { context.getSharedPreferences("jianji_update", Context.MODE_PRIVATE) }
    private val cancelled = AtomicBoolean(false)

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

            val code = connection.responseCode
            if (code != 200) {
                // P1-3：GitHub 未鉴权 API 限额 60 次/小时/IP，公共网络下极易耗尽；
                // 对 403/429 单独提示，避免用户误判为「网络被拦截」
                if (code == 403 || code == 429) {
                    return@withContext Result.failure(Exception(
                        "检查更新过于频繁（GitHub 接口限流，HTTP $code）。请 1 小时后再试，或前往 Releases 页手动下载：${releasesUrl()}"
                    ))
                }
                return@withContext Result.failure(Exception("GitHub API error: $code"))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }

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
    // 语义化比较版本号（实现见 com.example.jianji.utils.compareVersionNewer，便于单元测试；已修复 "-fixed" 后缀误判 P2-2d）
    private fun isNewerVersion(current: String, latest: String): Boolean =
        compareVersionNewer(current, latest)

    /**
     * 通过 HttpURLConnection 直接下载 APK（绕过 DownloadManager 的 file:// URI 暴露问题），
     * 下载完成后用 FileProvider 触发安装。下载与检查更新走同一网络通路。
     */
    suspend fun downloadAndInstall(url: String, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        cancelled.set(false)
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
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var copied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (cancelled.get()) {
                            output.flush()
                            apkFile.delete()
                            throw Exception("已取消下载")
                        }
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied * 100 / total).toInt())
                    }
                    output.flush()
                }
            }
            // 完整性校验：防止网络中断产生的残缺 APK 被当作“低版本”误判
            if (total > 0 && apkFile.length() != total) {
                apkFile.delete()
                throw Exception("下载文件不完整（已下载 ${apkFile.length()} / $total 字节），请重试或前往 GitHub 手动下载")
            }
            onProgress(100)
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
        markUpdateInstalled()
    }

    /**
     * 可靠读取 APK 的版本信息。
     * 某些 ROM 在 flags=0 时不填充 versionCode，故以签名标志再读一次作为兜底，
     * 避免把“版本号读不出来”误判为低版本而阻断正常更新。
     */
    private data class ApkVersion(val code: Long, val name: String)

    private fun readApkVersion(pm: PackageManager, apk: File): ApkVersion? {
        fun read(flags: Int): Pair<Long, String> {
            val info = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return 0L to ""
            val code = PackageInfoCompat.getLongVersionCode(info)
            val name = info.versionName ?: ""
            return code to name
        }
        var (code, name) = read(0)
        if (code <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val (c2, n2) = read(PackageManager.GET_SIGNING_CERTIFICATES)
            if (c2 > code) code = c2
            if (name.isEmpty()) name = n2
        }
        return if (code > 0 || name.isNotEmpty()) ApkVersion(code, name) else null
    }

    /**
     * 安装前自检：读取下载 APK 的包名 / versionName / versionCode / 签名，与已装应用比对。
     * 返回 null 表示可以安装；否则返回需要提示给用户的原因。
     *
     * 根因（反复出现“已安装更高版本”的真正原因）：
     * 1) 判定口径不统一——“检查更新”用 versionName 比较，这里却用 versionCode 比较，
     *    二者在本地/调试构建上容易脱节，造成误拦截或把问题甩给系统。
     * 2) Android 硬性规则：已装应用 versionCode 高于待装 APK 时，系统直接拒绝
     *    （INSTALL_FAILED_VERSION_DOWNGRADE），提示“已安装更高版本”。当设备上装的是
     *    本地/调试构建且 versionCode 高于发布版时，每次应用内更新都会触发此拦截——
     *    这不是正式版 bug，而是本地版本号偏高所致。
     * 本函数与“检查更新”统一用 versionName 语义比较；仅在下载包 versionName 不新于
     * 已装版本时才拦截，并给出精确定位（本地构建偏高 / 真实降级）与可执行建议。
     */
    private fun installBlockedReason(apk: File): String? {
        val pm = context.packageManager

        // 1) 包名一致性（轻量读取）
        val light = pm.getPackageArchiveInfo(apk.absolutePath, 0)
        if (light?.packageName != context.packageName) {
            return "安装包包名(${light?.packageName})与当前应用不一致，无法覆盖安装"
        }

        // 2) 可靠读取版本号（多方法兜底，避免把“读不出版本”误判为低版本）
        val apkVer = readApkVersion(pm, apk) ?: return "无法读取安装包信息，请到下载目录手动安装"

        val installedFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else 0
        val installed = try {
            pm.getPackageInfo(context.packageName, installedFlags)
        } catch (_: Exception) { null }

        if (installed != null) {
            val installedVc = PackageInfoCompat.getLongVersionCode(installed)
            val installedName = installed.versionName ?: ""

            // 3) 与“检查更新”保持同一判定口径：以 versionName 语义比较为准。
            //    下载包 versionName 比已装更新 → 视为合法更新，放行（最终交由系统校验）。
            //    仅当下载包 versionName 不新于已装时，才判定为降级/同版本而拦截。
            val downgrade = if (apkVer.name.isNotEmpty() && installedName.isNotEmpty()) {
                !isNewerVersion(installedName, apkVer.name)
            } else {
                apkVer.code > 0 && apkVer.code <= installedVc
            }
            if (downgrade) {
                val apkTag = if (apkVer.name.isNotEmpty()) "v${apkVer.name}" else "code ${apkVer.code}"
                val insTag = if (installedName.isNotEmpty()) "v$installedName" else "code $installedVc"
                // 精确定位：是“发布版确实更旧”，还是“设备上是本地/调试构建且版本号偏高”
                val higherLocal = apkVer.code > 0 && installedVc > apkVer.code
                return if (higherLocal) {
                    "无法安装：你设备当前版本 versionCode($installedVc) 高于要安装的发布版(${apkVer.code})。\n" +
                            "这通常是因为设备上装的是本地/调试构建（versionCode 偏高），并非正式版 bug。\n" +
                            "解决方法：先卸载当前应用，再安装正式版；或本地开发时不要把 versionCode 设得高于已发布版本。"
                } else {
                    "无法安装：下载的安装包($apkTag) 不高于已安装版本($insTag)，系统禁止降级或同版本覆盖安装。\n" +
                            "请先卸载当前应用，再安装正式版；或前往 GitHub 手动下载：${releasesUrl()}"
                }
            }

            // 4) 签名一致性（单独读取签名，不干扰版本号的可靠读取）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val archive = pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ) ?: return "无法读取安装包签名信息，请到下载目录手动安装"
                val same = certSet(installed.signingInfo) == certSet(archive.signingInfo)
                if (!same) {
                    return "下载的安装包与已安装应用签名不一致（设备上装的可能是调试/本地构建版）。\n" +
                            "Android 不允许不同签名的覆盖安装。请先卸载当前应用，再到 GitHub 安装正式版。"
                }
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
        val apkVer = readApkVersion(context.packageManager, f) ?: return false
        if (apkVer.code <= 0) return false
        val installedVc = try {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
        } catch (_: Exception) { 0L }
        return apkVer.code > installedVc
    }

    /** 安装本机已下载好的更新安装包（检查更新失败但仍已下好包时复用） */
    fun installLocalApk() {
        val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "jianji_update.apk")
        if (!f.exists()) {
            Toast.makeText(context, "未找到本地安装包", Toast.LENGTH_SHORT).show()
            return
        }
        // 自检：本地 APK 版本是否真正新于已装版本（防止上次更新残留的同级/旧包被误装）
        val apkVer = readApkVersion(context.packageManager, f)
        if (apkVer != null && apkVer.code > 0) {
            val installedVc = try {
                PackageInfoCompat.getLongVersionCode(
                    context.packageManager.getPackageInfo(context.packageName, 0)
                )
            } catch (_: Exception) { 0L }
            if (apkVer.code <= installedVc) {
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
        markUpdateInstalled()
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

    /** 取消正在进行的下载（由“检查更新”界面取消按钮调用，P2-2b） */
    fun cancelDownload() { cancelled.set(true) }

    /** 更新成功后调用：标记待清理，下次冷启动由 MainActivity 执行真正的缓存清理 */
    fun markUpdateInstalled() {
        updatePrefs.edit().putBoolean("pending_clear", true).apply()
    }

    /**
     * 清除所有更新包与更新缓存数据（APK 安装包 + 私有缓存目录中相关文件）。
     * 由“检查更新”流程在安装完成后标记，于下次应用冷启动时执行
     * （P2-2 收尾 + 用户新增要求：更新后清除所有更新包和更新缓存包数据）。
     */
    fun clearUpdateCache() {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val apk = File(dir, "jianji_update.apk")
            if (apk.exists()) apk.delete()
            context.cacheDir?.listFiles()?.forEach { f ->
                if (f.name.contains("jianji_update", ignoreCase = true) || f.name.endsWith(".apk", ignoreCase = true)) {
                    f.delete()
                }
            }
        } catch (_: Exception) { }
    }
}
