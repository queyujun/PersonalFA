package com.yingjing.pfa.data.ai

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yingjing.pfa.core.security.AiSecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore by preferencesDataStore(name = "ai_settings")

/**
 * AI 配置档案存取（DataStore）。API Key 永不经过这里——只写
 * [AiSecretStore]（AES/GCM 加密后存数据库键值表），本存储只持有
 * 档案列表、当前生效档案 id 与全局隐私同意。
 *
 * 兼容投影：[settings] 恒等于「当前生效档案」的字段视图（consented 来自全局 key），
 * 供 AiAssistant / 设置页摘要等既有消费方零改动使用。
 */
interface AiSettingsStore {
    /** 全部档案（预设 6 个固定 + 自定义若干）。 */
    val profiles: Flow<List<AiProfile>>

    /** 当前生效档案 id（null = 未选定，或指向已删除档案被读取时屏蔽为 null）。 */
    val activeProfileId: Flow<String?>

    /** 生效档案投影（无生效档案时为全默认值，consented 合自全局同意 key）。 */
    val settings: Flow<AiSettings>

    /** 全局隐私同意（用户级事实，不随档案切换重置）。 */
    val consented: Flow<Boolean>

    /** 新增或更新档案（按 id 匹配，不存在则追加）。 */
    suspend fun saveProfile(profile: AiProfile)

    /** 删除自定义档案（预设档案不可删）；同时清除该档案密钥。返回是否实际删除。 */
    suspend fun deleteProfile(profileId: String): Boolean

    /** 设定生效档案；null = 取消生效（备份导入无生效项时用）。 */
    suspend fun setActiveProfile(profileId: String?)

    /** 保存指定档案的 API Key（加密后写入数据库）；null/空串 = 清除。 */
    suspend fun setApiKey(profileId: String, raw: String?)

    /** 指定档案是否已保存 API Key。 */
    suspend fun hasApiKey(profileId: String): Boolean

    /** 读取指定档案的 API Key 明文（仅构造请求时使用，绝不进日志/prompt）。 */
    suspend fun apiKey(profileId: String): String?

    /** 当前生效档案的 API Key 明文（AiAssistant 兼容入口）。 */
    suspend fun apiKey(): String?

    suspend fun setConsented(value: Boolean)
}

@Singleton
class AiSettingsStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretStore: AiSecretStore,
) : AiSettingsStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val profileListSerializer = ListSerializer(AiProfile.serializer())

    override val profiles: Flow<List<AiProfile>> = context.aiDataStore.data
        .onStart { ensureMigrated() }
        .map { prefs -> decodeProfiles(prefs[PROFILES]).ifEmpty { defaultProfiles() } }

    override val activeProfileId: Flow<String?> = context.aiDataStore.data
        .onStart { ensureMigrated() }
        .map { prefs ->
            prefs[ACTIVE_PROFILE_ID]?.takeIf { id -> id in profilesForIdCheck(prefs) }
        }

    override val consented: Flow<Boolean> = context.aiDataStore.data
        .onStart { ensureMigrated() }
        .map { prefs -> prefs[CONSENTED] ?: false }

    override val settings: Flow<AiSettings> = context.aiDataStore.data
        .onStart { ensureMigrated() }
        .map { prefs ->
            val list = decodeProfiles(prefs[PROFILES]).ifEmpty { defaultProfiles() }
            val active = prefs[ACTIVE_PROFILE_ID]?.let { id -> list.firstOrNull { it.id == id } }
            active.toSettings(prefs[CONSENTED] ?: false)
        }

    override suspend fun saveProfile(profile: AiProfile) {
        ensureMigrated()
        context.aiDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[PROFILES]).ifEmpty { defaultProfiles() }
            val next = if (current.any { it.id == profile.id }) {
                current.map { if (it.id == profile.id) profile else it }
            } else {
                current + profile
            }
            prefs[PROFILES] = json.encodeToString(profileListSerializer, next)
            // 无生效档案时自动激活（新装最短路径）；已有生效档案则绝不改动——防误触。
            if (prefs[ACTIVE_PROFILE_ID] == null) {
                val candidate = if (profile.isConfigured) profile else next.firstOrNull { it.isConfigured }
                if (candidate != null) prefs[ACTIVE_PROFILE_ID] = candidate.id
            }
        }
    }

    override suspend fun deleteProfile(profileId: String): Boolean {
        ensureMigrated()
        var removed = false
        context.aiDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[PROFILES]).ifEmpty { defaultProfiles() }
            val target = current.firstOrNull { it.id == profileId } ?: return@edit
            if (target.isPreset) return@edit
            prefs[PROFILES] = json.encodeToString(profileListSerializer, current - target)
            if (prefs[ACTIVE_PROFILE_ID] == profileId) prefs.remove(ACTIVE_PROFILE_ID)
            removed = true
        }
        if (removed) secretStore.clear(profileId)
        return removed
    }

    override suspend fun setActiveProfile(profileId: String?) {
        ensureMigrated()
        context.aiDataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(ACTIVE_PROFILE_ID)
                return@edit
            }
            val list = decodeProfiles(prefs[PROFILES]).ifEmpty { defaultProfiles() }
            if (list.any { it.id == profileId }) prefs[ACTIVE_PROFILE_ID] = profileId
        }
    }

    override suspend fun setApiKey(profileId: String, raw: String?) {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            secretStore.clear(profileId)
        } else {
            secretStore.write(profileId, trimmed)
        }
    }

    override suspend fun hasApiKey(profileId: String): Boolean = secretStore.exists(profileId)

    override suspend fun apiKey(profileId: String): String? = secretStore.read(profileId)

    override suspend fun apiKey(): String? {
        val id = activeProfileId.first() ?: return null
        return secretStore.read(id)
    }

    override suspend fun setConsented(value: Boolean) {
        ensureMigrated()
        context.aiDataStore.edit { prefs ->
            prefs[CONSENTED] = value
        }
    }

    // ---- 旧版单配置迁移（DataStore 6 扁平 key → 档案列表），幂等 ----

    private val migrationMutex = Mutex()
    private var migrated = false

    /** 首次访问触发一次迁移；已迁移则零开销直通。 */
    private suspend fun ensureMigrated() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return
            migrateLegacyIfNeeded()
            migrated = true
        }
    }

    /**
     * 旧版（多档案改造前）单配置迁移，每步幂等：
     * ① profiles 未初始化时：建预设 6 档案，旧配置写入对应档案（providerId=custom 则建
     * `custom_legacy` 档案）并设为生效；② consented 复制到全局 key；③ 清旧 6 key；
     * ④ 旧全局单 key 划归迁移目标档案（[AiSecretStore.migrateLegacySingleKeyTo]，
     * 失败时目标 id 留在 DataStore，下次启动重试——用固定目标避免期间切换生效档案后密钥落错位置）。
     */
    private suspend fun migrateLegacyIfNeeded() {
        // 快速路径：档案列表已就位且无遗留密钥归档目标。
        val snapshot = context.aiDataStore.data.first()
        if (snapshot[PROFILES] != null && snapshot[LEGACY_SECRET_TARGET] == null) return

        var secretTarget: String? = null
        context.aiDataStore.edit { prefs ->
            if (prefs[PROFILES] == null) {
                val legacyProvider = AiProviderPreset.fromId(prefs[LEGACY_PROVIDER_ID])
                val legacyProfile = prefs[LEGACY_BASE_URL]?.takeIf { it.isNotBlank() }?.let { base ->
                    AiProfile(
                        id = if (legacyProvider == AiProviderPreset.CUSTOM) LEGACY_CUSTOM_ID
                        else AiProfile.presetIdOf(legacyProvider),
                        providerId = legacyProvider.id,
                        baseUrl = base,
                        model = prefs[LEGACY_MODEL] ?: "",
                        protocol = AiApiProtocol.fromId(prefs[LEGACY_PROTOCOL]),
                        includeDetails = prefs[LEGACY_INCLUDE_DETAILS] ?: true,
                    )
                }
                val next = defaultProfiles().let { defaults ->
                    when {
                        legacyProfile == null -> defaults
                        defaults.any { it.id == legacyProfile.id } ->
                            defaults.map { if (it.id == legacyProfile.id) legacyProfile else it }
                        else -> defaults + legacyProfile
                    }
                }
                prefs[PROFILES] = json.encodeToString(profileListSerializer, next)
                if (legacyProfile != null) {
                    prefs[ACTIVE_PROFILE_ID] = legacyProfile.id
                    prefs[LEGACY_SECRET_TARGET] = legacyProfile.id
                }
                if (prefs[LEGACY_CONSENTED] == true) prefs[CONSENTED] = true
                prefs.remove(LEGACY_PROVIDER_ID)
                prefs.remove(LEGACY_BASE_URL)
                prefs.remove(LEGACY_MODEL)
                prefs.remove(LEGACY_PROTOCOL)
                prefs.remove(LEGACY_INCLUDE_DETAILS)
                prefs.remove(LEGACY_CONSENTED)
            }
            secretTarget = prefs[LEGACY_SECRET_TARGET]
        }
        val target = secretTarget ?: return
        if (runCatching { secretStore.migrateLegacySingleKeyTo(target) }.isSuccess) {
            context.aiDataStore.edit { it.remove(LEGACY_SECRET_TARGET) }
        }
    }

    private fun decodeProfiles(encoded: String?): List<AiProfile> =
        encoded?.let { runCatching { json.decodeFromString(profileListSerializer, it) }.getOrNull() }
            ?: emptyList()

    private fun profilesForIdCheck(prefs: Preferences): List<String> =
        decodeProfiles(prefs[PROFILES]).ifEmpty { defaultProfiles() }.map { it.id }

    private fun defaultProfiles(): List<AiProfile> =
        AiProviderPreset.entries.filter { it != AiProviderPreset.CUSTOM }
            .map { AiProfile(id = AiProfile.presetIdOf(it), providerId = it.id) }

    private fun AiProfile?.toSettings(consented: Boolean): AiSettings =
        if (this == null) {
            AiSettings(consented = consented)
        } else {
            AiSettings(
                providerId = providerId,
                baseUrl = baseUrl,
                model = model,
                protocol = protocol,
                includeDetails = includeDetails,
                tone = tone,
                maxTokens = maxTokens,
                consented = consented,
            )
        }

    private companion object {
        val PROFILES = stringPreferencesKey("ai_profiles")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("ai_active_profile")
        val CONSENTED = booleanPreferencesKey("ai_consent")

        /** 密钥归档目标（迁移中途失败时留档，下次启动重试）。 */
        val LEGACY_SECRET_TARGET = stringPreferencesKey("ai_legacy_secret_target")

        // 旧版单配置的 6 个扁平 key（迁移源）。
        val LEGACY_PROVIDER_ID = stringPreferencesKey("provider_id")
        val LEGACY_BASE_URL = stringPreferencesKey("base_url")
        val LEGACY_MODEL = stringPreferencesKey("model")
        val LEGACY_PROTOCOL = stringPreferencesKey("protocol")
        val LEGACY_INCLUDE_DETAILS = booleanPreferencesKey("include_details")
        val LEGACY_CONSENTED = booleanPreferencesKey("consented")

        /** 旧版 provider=custom 配置迁移后的档案 id。 */
        const val LEGACY_CUSTOM_ID = "custom_legacy"
    }
}
