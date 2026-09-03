package com.yingjing.pfa.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 功能密钥（OpenAI 兼容服务的 API Key）的安全存取。
 *
 * 与 [DatabaseKeyProvider] 同构：明文不落盘，用 Android Keystore 的 AES/GCM 密钥
 * 加密后写入独立文件（`ai_key.bin`），密钥别名独立（`pfa_ai_key`），不与数据库口令混用。
 *
 * Keystore 可能因锁屏凭据变更 / 恢复备份等场景失效（解密抛异常）——[read] 容错返回 null，
 * 由上层提示用户重新录入，而非崩溃。
 */
interface AiSecretStore {
    /** 是否已保存过密钥（文件存在即算，即使解密失败也提示重新录入而非当作未配置）。 */
    fun exists(): Boolean

    /** 读取并解密 API Key；不存在或解密失败（Keystore 失效）返回 null。 */
    fun read(): String?

    /** 加密并保存 API Key。 */
    fun write(value: String)

    /** 清除已保存的密钥。 */
    fun clear()
}

@Singleton
class KeystoreAiSecretStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AiSecretStore {

    private val blobFile: File get() = File(context.filesDir, BLOB_NAME)

    override fun exists(): Boolean = blobFile.exists()

    override fun read(): String? {
        if (!blobFile.exists()) return null
        return try {
            String(decrypt(blobFile.readBytes()), Charsets.UTF_8)
        } catch (_: Exception) {
            // Keystore 失效（换机恢复 / 锁屏凭据变更等）→ 视为密钥不可用，提示重新录入。
            null
        }
    }

    override fun write(value: String) {
        blobFile.writeBytes(encrypt(value.toByteArray(Charsets.UTF_8)))
    }

    override fun clear() {
        if (blobFile.exists()) blobFile.delete()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    // blob 布局与 DatabaseKeyProvider 一致：[1B iv 长度][iv][ciphertext]
    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(data)
        return ByteArray(1 + iv.size + cipherText.size).also { out ->
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(cipherText, 0, out, 1 + iv.size, cipherText.size)
        }
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val ivLength = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLength)
        val cipherText = blob.copyOfRange(1 + ivLength, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pfa_ai_key"
        const val BLOB_NAME = "ai_key.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
