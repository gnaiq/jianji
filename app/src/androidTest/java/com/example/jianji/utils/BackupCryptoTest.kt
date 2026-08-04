package com.example.jianji.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验收 P6-1：备份加密（PBKDF2 + AES-GCM）往返一致，且密文不同于明文。
 */
@RunWith(AndroidJUnit4::class)
class BackupCryptoTest {

    @Test
    fun `加密后密文不以明文开头 且可解密还原`() {
        val plain = """{"version":4,"transactions":[{"id":1,"amount":12.34}]}"""
        val ct = BackupCrypto.encrypt(plain, "s3cret-pass")
        assertTrue("应标记为加密格式", BackupCrypto.isEncrypted(ct))
        assertFalse("密文不应包含明文 JSON", ct.contains("\"version\":4"))
        val back = BackupCrypto.decrypt(ct, "s3cret-pass")
        assertEquals(plain, back)
    }

    @Test
    fun `错误口令解密失败`() {
        val plain = "hello-backup"
        val ct = BackupCrypto.encrypt(plain, "right-pass")
        var threw = false
        try {
            BackupCrypto.decrypt(ct, "wrong-pass")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("错误口令应解密失败（GCM 认证失败）", threw)
    }

    @Test
    fun `相同明文每次加密密文不同 IV随机`() {
        val plain = "repeat"
        val a = BackupCrypto.encrypt(plain, "p")
        val b = BackupCrypto.encrypt(plain, "p")
        assertTrue("每次应产生不同密文（随机 salt/iv）", a != b)
        assertEquals(plain, BackupCrypto.decrypt(a, "p"))
        assertEquals(plain, BackupCrypto.decrypt(b, "p"))
    }
}
