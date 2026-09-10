package com.yingjing.pfa.core.security

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.data.local.AppMetaDao
import com.yingjing.pfa.data.local.AppMetaEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * [DatabaseAiSecretStore] 的行为验证（Robolectric 内存库）。
 *
 * 覆盖：按档案写入→读回明文、密文不以明文入库、覆盖写、清除、损坏密文容错、
 * 档案间密文隔离、旧 ai_secret_key 单行归档（幂等）、旧 ai_key.bin 一次性迁移。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseAiSecretStoreTest {

    private lateinit var db: AppDatabase
    private lateinit var appMetaDao: AppMetaDao
    private lateinit var legacyFile: File
    private lateinit var store: DatabaseAiSecretStore
    private lateinit var fixedKeyProvider: DatabaseKeyProvider

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        appMetaDao = db.appMetaDao()
        legacyFile = File(context.filesDir.apply { mkdirs() }, "ai_key.bin").apply { delete() }
        // 固定数据库口令，避免依赖 Robolectric Keystore（不可用）。
        fixedKeyProvider = FixedKeyProvider()
        store = DatabaseAiSecretStore(context, appMetaDao, fixedKeyProvider)
    }

    @After
    fun tearDown() {
        legacyFile.delete()
        db.close()
    }

    @Test
    fun write_thenRead_returnsPlaintext_andStoredValueIsNotPlaintext() = runTest {
        val id = "preset_deepseek"
        assertFalse(store.exists(id))
        store.write(id, "sk-test-123456")

        assertTrue(store.exists(id))
        assertEquals("sk-test-123456", store.read(id))
        // 库里存的是 Base64 密文，绝不含明文。
        val stored = appMetaDao.get("ai_secret_$id")!!
        assertFalse(stored.contains("sk-test-123456"))
        assertNotEquals("sk-test-123456", stored)
    }

    @Test
    fun write_perProfile_isIsolated() = runTest {
        // 每档案独立一行：互不覆盖、互不可读。
        store.write("preset_deepseek", "sk-deep")
        store.write("preset_hunyuan", "sk-hy")

        assertEquals("sk-deep", store.read("preset_deepseek"))
        assertEquals("sk-hy", store.read("preset_hunyuan"))
        assertFalse(store.exists("preset_openai"))
        assertNull(store.read("preset_openai"))
    }

    @Test
    fun write_overwritesPreviousKey() = runTest {
        store.write("preset_deepseek", "sk-first")
        store.write("preset_deepseek", "sk-second")
        assertEquals("sk-second", store.read("preset_deepseek"))
    }

    @Test
    fun clear_removesKey() = runTest {
        store.write("preset_deepseek", "sk-gone")
        store.clear("preset_deepseek")
        assertFalse(store.exists("preset_deepseek"))
        assertNull(store.read("preset_deepseek"))
    }

    @Test
    fun read_corruptedCiphertext_returnsNullInsteadOfCrash() = runTest {
        store.write("preset_deepseek", "sk-good")
        // 模拟密文被篡改/损坏：翻转一段字节。
        val stored = appMetaDao.get("ai_secret_preset_deepseek")!!
        val corrupted = stored.toCharArray().also { it[10] = if (it[10] == 'A') 'B' else 'A' }.concatToString()
        appMetaDao.put(AppMetaEntity("ai_secret_preset_deepseek", corrupted))
        assertNull(store.read("preset_deepseek"))
    }

    @Test
    fun migrateLegacySingleKey_movesOldRow_onceAndIdempotent() = runTest {
        // 旧版全局单 key 行（多档案改造前的存储位）。
        appMetaDao.put(AppMetaEntity("ai_secret_key", "legacy-ciphertext-blob"))
        store.migrateLegacySingleKeyTo("preset_hunyuan")

        assertNull(appMetaDao.get("ai_secret_key")) // 旧行已删
        assertEquals("legacy-ciphertext-blob", appMetaDao.get("ai_secret_preset_hunyuan"))

        // 幂等：旧行已不存在时再次调用无副作用。
        store.migrateLegacySingleKeyTo("preset_deepseek")
        assertNull(appMetaDao.get("ai_secret_key"))
        assertNull(appMetaDao.get("ai_secret_preset_deepseek"))
        assertEquals("legacy-ciphertext-blob", appMetaDao.get("ai_secret_preset_hunyuan"))
    }

    @Test
    fun migrateLegacySingleKey_targetAlreadyHasKey_keepsTarget() = runTest {
        appMetaDao.put(AppMetaEntity("ai_secret_key", "legacy-ciphertext-blob"))
        store.write("preset_hunyuan", "sk-existing")

        store.migrateLegacySingleKeyTo("preset_hunyuan")
        // 目标已有 key：不动目标行；但旧行仍被清理（视为归档完成）。
        assertEquals("sk-existing", store.read("preset_hunyuan"))
        assertNull(appMetaDao.get("ai_secret_key"))
    }

    @Test
    fun legacyFile_isMigratedIntoDb_thenDeleted() = runTest {
        // 用旧方案无法在 Robolectric 里造合法 Keystore 密文 → 直接验证「迁移失败也删文件、库为空」，
        // 以及「文件不存在时一切照旧」。成功迁移路径依赖真机 Keystore，不在此测。
        legacyFile.writeBytes(ByteArray(40) { it.toByte() }) // Robolectric 无 AndroidKeyStore，解密必抛安全异常
        assertFalse(store.exists("preset_deepseek"))
        assertNull(store.read("preset_deepseek"))
        assertFalse(legacyFile.exists()) // 失效文件被清理，不会反复尝试
        // 清理后再次访问无副作用。
        assertNull(store.read("preset_deepseek"))
    }

    @Test
    fun noLegacyFile_plainRoundTrip() = runTest {
        // 覆盖安装场景：无旧文件，纯数据库读写路径。
        store.write("preset_deepseek", "sk-fresh-install")
        assertEquals("sk-fresh-install", store.read("preset_deepseek"))
    }

    /** 固定口令的 DatabaseKeyProvider 替身：绕开 Robolectric 缺失的 Android Keystore。 */
    private class FixedKeyProvider : DatabaseKeyProvider(ApplicationProvider.getApplicationContext()) {
        override fun getOrCreatePassphrase(): ByteArray = ByteArray(32) { (it + 1).toByte() }
    }

    @Test
    fun differentPassphrase_cannotDecrypt() = runTest {
        // 换口令（模拟 db_pass.bin 被换）→ 旧密文解不开，返回 null 而非崩溃。
        store.write("preset_deepseek", "sk-under-old-key")
        val otherStore = DatabaseAiSecretStore(
            ApplicationProvider.getApplicationContext(),
            appMetaDao,
            OtherKeyProvider(),
        )
        assertNull(otherStore.read("preset_deepseek"))
    }

    private class OtherKeyProvider : DatabaseKeyProvider(ApplicationProvider.getApplicationContext()) {
        override fun getOrCreatePassphrase(): ByteArray = ByteArray(32) { (it + 100).toByte() }
    }
}
