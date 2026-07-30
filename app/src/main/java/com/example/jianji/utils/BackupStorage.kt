package com.example.jianji.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

data class BackupFileEntry(val uri: Uri, val name: String, val size: Long)

/**
 * 备份存储：写入系统「下载」共享目录（MediaStore），卸载 APP 后依然保留，
 * 重新安装可从这里恢复。低版本回退到应用私有目录。
 */
object BackupStorage {
    private const val PREFIX = "简记备份_"

    fun save(context: Context, fileName: String, mimeType: String, content: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw RuntimeException("无法写入共享存储")
            resolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: throw RuntimeException("无法打开输出流")
            return fileName
        } else {
            // 公共 Download 目录：卸载 APP 后依然保留，重新安装可恢复
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, fileName)
            file.writeText(content)
            return file.name
        }
    }

    private const val AUTO_PREFIX = "简记备份_自动"
    private const val AUTO_KEEP = 3

    /**
     * 自动备份：每次写入新的带时间戳文件并只保留最近 AUTO_KEEP 份（轮转）。
     * 相比旧实现的「单文件原地覆盖」：
     * - 消除 openOutputStream 默认 "w" 模式在部分 OEM 上不截断导致的 JSON 尾部残留损坏风险；
     * - 避免单点覆盖——某次备份内容异常时仍有前几份可回退。
     * 历史遗留的「简记备份_自动.json」同样匹配 AUTO_PREFIX，会随轮转被自然清理。
     */
    fun saveAutoBackup(context: Context, content: String) {
        val ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        save(context, "${AUTO_PREFIX}_$ts.json", "application/json", content)
        list(context)
            .filter { it.name.startsWith(AUTO_PREFIX) }
            .sortedByDescending { it.name } // 文件名内嵌时间戳，字典序即时间序；遗留无时间戳旧文件排最末优先清理
            .drop(AUTO_KEEP)
            .forEach { runCatching { delete(context, it.uri) } }
    }

    fun list(context: Context): List<BackupFileEntry> {
        val result = mutableListOf<BackupFileEntry>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val proj = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE
            )
            val sel = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selArgs = arrayOf("$PREFIX%")
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, sel, selArgs,
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx)
                    val size = cursor.getLong(sizeIdx)
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    result.add(BackupFileEntry(uri, name, size))
                }
            }
        } else {
            // pre-Q：与 save() 写入同一公共 Download 目录，避免「写公共目录、读私有目录」的不一致（P0-3）
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir?.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { result.add(BackupFileEntry(Uri.fromFile(it), it.name, it.length())) }
        }
        return result
    }

    fun read(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            return stream.bufferedReader().readText()
        }
        throw RuntimeException("无法读取备份文件")
    }

    /** 删除指定备份文件（Q+ 走 MediaStore，低版本直接删文件） */
    fun delete(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.delete(uri, null, null)
        } else {
            val path = uri.path ?: return
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }

    /** 删除全部备份文件 */
    fun deleteAll(context: Context) {
        list(context).forEach { delete(context, it.uri) }
    }
}