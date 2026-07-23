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

    private const val AUTO_NAME = "简记备份_自动.json"

    /** 自动备份：覆盖同名条目，保证共享目录中仅一份，卸载后仍可恢复 */
    fun saveAutoBackup(context: Context, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val sel = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selArgs = arrayOf(AUTO_NAME)
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID), sel, selArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    return
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, AUTO_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw RuntimeException("无法写入共享存储")
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, AUTO_NAME).writeText(content)
        }
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
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
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
}
