package com.yingjing.pfa.core.security

import android.content.Context
import com.yingjing.pfa.data.local.AppMetaDao
import com.yingjing.pfa.data.local.AppMetaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AiSecretStore] 的数据库实现：密文存 Room 键值表（app_meta），AES/GCM 应用层加密，
 * 按配置档案一行（key = `ai_secret_<profileId>`）。
 *
 * ## 存储布局
 * `app_meta` 行 key = `ai_secret_<profileId>`，value = Base64(`[1B iv长度][iv][ciphertext]`)。
 * 另有两类过渡行：旧版全局单 key（`ai_secret_key`）与更早的 Keystore 文件（`ai_key.bin`），
 * 均经 [migrateLegacySingleKeyTo] / [migrateLegacyBlobIfNeeded] 收敛到档案行。
 *
 * ## 加密密钥来源（关键设计）
 * 由 SQLCipher 数据库口令（[DatabaseKeyProvider] 的 32 字节随机口令）经 SHA-256 派生：
 * - 口令本身同样由 Android Keystore 保护落盘（`db_pass.bin`），明文永不落盘；
 * - 生命周期与数据库对齐——只要数据库还能打开（覆盖安装后口令文件仍在），密钥就能解密；
 * - 安全边界即数据库口令的保密性：口令不出设备，单独拿到密文数据库或本类密文均无法解密。
 *
 * ## 覆盖安装兼容
 * APK 覆盖安装不触碰 filesDir（`db_pass.bin` 与 `pfa.db` 均保留），Keystore 条目也保留
 * （Keystore 失效场景仅限锁屏凭据重置/恢复备份，与覆盖安装无关），因此密钥直接可读。
 *
 * ## 旧数据迁移
 * - 历史 Keystore 文件方案（`ai_key.bin`）在首次访问时一次性导入到旧版单 key 行
 *   （`ai_secret_key`），再由上层归档到具体档案；文件能解密则导入并删文件；
 *   解密失败（Keystore 已永久失效）则删除失效文件，视为未配置，由上层提示重新录入。
 *   入库失败（可重试错误）保留文件待下次。
 * - 旧版全局单 key 行（`ai_secret_key`）由上层调用 [migrateLegacySingleKeyTo] 划归档案。
 */
@Singleton
class DatabaseAiSecretStore @Inject constructor(
    @ApplicationContext context: Context,
    private val appMetaDao: AppMetaDao,
    private val databaseKeyProvider: DatabaseKeyProvider,
) : AiSecretStore {

    /** 旧方案文件（filesDir/ai_key.bin）；存在则首次访问时尝试迁移入库。 */
    private val legacyBlobFile: File = File(context.filesDir, LEGACY_BLOB_NAME)

    override suspend fun exists(profileId: String): Boolean = withContext(Dispatchers.IO) {
        migrateLegacyBlobIfNeeded()
        appMetaDao.get(metaKey(profileId)) != null
    }

    override suspend fun read(profileId: String): String? = withContext(Dispatchers.IO) {
        migrateLegacyBlobIfNeeded()
        val stored = appMetaDao.get(metaKey(profileId)) ?: return@withContext null
        try {
            String(decrypt(Base64.getDecoder().decode(stored)), Charsets.UTF_8)
        } catch (_: Exception) {
            // 密文损坏 / 派生密钥不符（理论上仅数据库被外部篡改）→ 视为不可用，提示重录。
            null
        }
    }

    override suspend fun write(profileId: String, value: String) = withContext(Dispatchers.IO) {
        migrateLegacyBlobIfNeeded()
        appMetaDao.put(
            AppMetaEntity(key = metaKey(profileId), value = Base64.getEncoder().encodeToString(encrypt(value.toByteArray(Charsets.UTF_8)))),
        )
    }

    override suspend fun clear(profileId: String) = withContext(Dispatchers.IO) {
        migrateLegacyBlobIfNeeded()
        appMetaDao.delete(metaKey(profileId))
    }

    override suspend fun migrateLegacySingleKeyTo(profileId: String) = withContext(Dispatchers.IO) {
        migrateLegacyBlobIfNeeded()
        val legacy = appMetaDao.get(LEGACY_META_KEY) ?: return@withContext
        if (appMetaDao.get(metaKey(profileId)) == null) {
            appMetaDao.put(AppMetaEntity(key = metaKey(profileId), value = legacy))
        }
        // 目标已有 key 时旧值已被取代，仍删旧行——归档视为完成，不留待重试。
        appMetaDao.delete(LEGACY_META_KEY)
    }

    /**
     * 一次性迁移旧 Keystore 文件方案到旧版单 key 行（`ai_secret_key` 中转，
     * 由上层归档到具体档案）。删除源文件的时机区分结果：
     * 迁移成功 / 文件已解不开（Keystore 永久失效）→ 删文件；入库失败（可重试错误）→ 保留文件下次再试。
     */
    private suspend fun migrateLegacyBlobIfNeeded() {
        if (!legacyBlobFile.exists()) return
        val result = runCatching {
            val plain = decryptLegacy(legacyBlobFile.readBytes())
            appMetaDao.get(LEGACY_META_KEY) ?: appMetaDao.put(
                AppMetaEntity(
                    key = LEGACY_META_KEY,
                    value = Base64.getEncoder().encodeToString(encrypt(plain)),
                ),
            )
        }
        when (result.exceptionOrNull()) {
            null -> {
                legacyBlobFile.delete()
                deleteLegacyKeyEntry()
            }
            is GeneralSecurityException -> {
                // 旧密钥条目缺失 / 密文解不开 / Keystore 本身异常：密钥不可恢复，清理后视为未配置。
                legacyBlobFile.delete()
            }
            else -> {
                // CancellationException / 数据库写入失败等可重试错误：保留文件，下次访问再试。
            }
        }
    }

    // ---- 应用层 AES/GCM（密钥自 DB 口令派生）----

    private fun derivedKey(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(databaseKeyProvider.getOrCreatePassphrase() + DERIVE_SALT)
        return SecretKeySpec(digest, "AES")
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
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
        cipher.init(Cipher.DECRYPT_MODE, derivedKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    // ---- 旧文件解密（Keystore 别名 pfa_ai_key，迁移后即废弃）----

    private fun decryptLegacy(blob: ByteArray): ByteArray {
        val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = (keyStore.getEntry(LEGACY_KEY_ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.secretKey
            ?: throw UnrecoverableKeyException("legacy keystore entry missing")
        val ivLength = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLength)
        val cipherText = blob.copyOfRange(1 + ivLength, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    /** 迁移完成后删除 Keystore 中废弃的旧密钥条目（尽力而为，失败不影响主流程）。 */
    private fun deleteLegacyKeyEntry() {
        runCatching {
            java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .deleteEntry(LEGACY_KEY_ALIAS)
        }
    }

    private fun metaKey(profileId: String): String = "ai_secret_$profileId"

    private companion object {
        /** 旧版全局单 key 行（多档案改造前的存储位）。 */
        const val LEGACY_META_KEY = "ai_secret_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val LEGACY_KEY_ALIAS = "pfa_ai_key"
        const val LEGACY_BLOB_NAME = "ai_key.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val IV_BYTES = 12
        val DERIVE_SALT = byteArrayOf(0x50, 0x46, 0x41, 0x2D, 0x41, 0x49, 0x2D, 0x4B) // "PFA-AI-K"
    }
}
