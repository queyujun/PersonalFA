package com.yingjing.pfa.core.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件加密：AES-256-GCM，密钥由用户口令经 PBKDF2 派生。
 *
 * 文件结构：MAGIC(4) + salt(16) + iv(12) + ciphertext。口令错误时 [decrypt] 返回 null。
 */
object BackupCrypto {

    private val MAGIC = "PFA1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt))
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        return MAGIC + salt + iv + cipherText
    }

    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray? {
        val headerLen = MAGIC.size + SALT_BYTES + IV_BYTES
        if (blob.size <= headerLen) return null
        if (!blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        val salt = blob.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = blob.copyOfRange(MAGIC.size + SALT_BYTES, headerLen)
        val cipherText = blob.copyOfRange(headerLen, blob.size)
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherText)
        }.getOrNull()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
