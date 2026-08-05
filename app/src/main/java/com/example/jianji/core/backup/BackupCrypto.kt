package com.example.jianji.core.backup

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// 注：Base64 用 android.util.Base64（minSdk 24 无 java.util.Base64），其 NO_WRAP 与
// java.util.Base64 的 NO_WRAP 行为一致；单元测试对 BackupCrypto 走 androidTest（Instrumentation）。

/**
 * 备份加密：PBKDF2 + AES-GCM（修复 P6-1 备份明文落盘）。
 *
 * 设计取舍（见 docs/02b-数据库设计方案.md §备份加密）：
 *  - 不采用 Android Keystore：其密钥随 APP 卸载销毁，用户重装后无法解密历史备份，违背
 *    「下载目录备份卸载后仍可恢复」的核心诉求。改为口令派生密钥，口令由用户在备份/恢复时输入。
 *  - AES/GCM/NoPadding，12 字节随机 IV（每次加密独立），PBKDF2withHmacSHA256 迭代 120000。
 *
 * 输出格式（单行）：  v1:<base64(salt)>:<base64(iv)>:<base64(ciphertext)>
 * 明文旧备份（不以 "v1:" 开头）仍可导入，向下兼容。
 */
object BackupCrypto {
    private const val PREFIX = "v1:"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val ANDROID_BASE64_FLAGS = android.util.Base64.NO_WRAP

    fun isEncrypted(content: String): Boolean = content.startsWith(PREFIX)

    fun encrypt(plainJson: String, passphrase: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ct = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return buildString {
            append(PREFIX)
            append(Base64.encodeToString(salt, ANDROID_BASE64_FLAGS))
            append(':')
            append(Base64.encodeToString(iv, ANDROID_BASE64_FLAGS))
            append(':')
            append(Base64.encodeToString(ct, ANDROID_BASE64_FLAGS))
        }
    }

    fun decrypt(content: String, passphrase: String): String {
        require(isEncrypted(content)) { "不是加密备份格式" }
        val parts = content.removePrefix(PREFIX).split(':')
        require(parts.size == 3) { "加密备份格式损坏" }
        val salt = Base64.decode(parts[0], ANDROID_BASE64_FLAGS)
        val iv = Base64.decode(parts[1], ANDROID_BASE64_FLAGS)
        val ct = Base64.decode(parts[2], ANDROID_BASE64_FLAGS)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val pt = cipher.doFinal(ct)
        return String(pt, Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }
}
