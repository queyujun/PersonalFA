package com.yingjing.pfa.data.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore by preferencesDataStore(name = "ai_settings")

/**
 * AI 配置存取（DataStore）。API Key 永不经过这里——只写
 * [com.yingjing.pfa.core.security.AiSecretStore]（Keystore 加密落盘），
 * 本存储只持有「是否有 key」之外的常规配置。
 */
interface AiSettingsStore {
    val settings: Flow<AiSettings>

    suspend fun save(settings: AiSettings)

    /** 保存 API Key（写入 Keystore 加密文件）；null/空串 = 清除。 */
    suspend fun setApiKey(raw: String?)

    /** 是否已保存 API Key。 */
    suspend fun hasApiKey(): Boolean

    /** 读取 API Key 明文（仅 AiAssistant 构造请求时使用，绝不进日志/prompt）。 */
    suspend fun apiKey(): String?
}

@Singleton
class AiSettingsStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretStore: com.yingjing.pfa.core.security.AiSecretStore,
) : AiSettingsStore {

    override val settings: Flow<AiSettings> = context.aiDataStore.data.map { prefs ->
        AiSettings(
            providerId = prefs[PROVIDER_ID] ?: AiProviderPreset.DEEPSEEK.id,
            baseUrl = prefs[BASE_URL] ?: "",
            model = prefs[MODEL] ?: "",
            protocol = AiApiProtocol.fromId(prefs[PROTOCOL]),
            includeDetails = prefs[INCLUDE_DETAILS] ?: true,
            consented = prefs[CONSENTED] ?: false,
        )
    }

    override suspend fun save(settings: AiSettings) {
        context.aiDataStore.edit { prefs ->
            prefs[PROVIDER_ID] = settings.providerId
            prefs[BASE_URL] = settings.baseUrl
            prefs[MODEL] = settings.model
            prefs[PROTOCOL] = settings.protocol.id
            prefs[INCLUDE_DETAILS] = settings.includeDetails
            prefs[CONSENTED] = settings.consented
        }
    }

    override suspend fun setApiKey(raw: String?) {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            secretStore.clear()
        } else {
            secretStore.write(trimmed)
        }
    }

    override suspend fun hasApiKey(): Boolean =
        secretStore.exists() && secretStore.read() != null

    override suspend fun apiKey(): String? = secretStore.read()

    private companion object {
        val PROVIDER_ID = stringPreferencesKey("provider_id")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val PROTOCOL = stringPreferencesKey("protocol")
        val INCLUDE_DETAILS = booleanPreferencesKey("include_details")
        val CONSENTED = booleanPreferencesKey("consented")
    }
}
