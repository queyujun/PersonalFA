package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiSettings
import com.yingjing.pfa.data.ai.AiSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 内存版 AI 档案存储；key 明文保存在内存 Map（模拟数据库密钥层的按档案行），
 * 供 ViewModel / 备份测试使用。不实现旧数据迁移（内存无旧 key 可迁）。
 */
class FakeAiSettingsStore : AiSettingsStore {

    private val _profiles = MutableStateFlow<List<AiProfile>>(emptyList())
    private val _activeId = MutableStateFlow<String?>(null)
    private val _consented = MutableStateFlow(false)

    /** profileId → key 明文（仅测试断言用）。 */
    val keys: MutableMap<String, String> = mutableMapOf()

    override val profiles: Flow<List<AiProfile>> = _profiles

    override val activeProfileId: Flow<String?> = _activeId

    override val consented: Flow<Boolean> = _consented

    override val settings: Flow<AiSettings> =
        combine(_profiles, _activeId, _consented) { list, activeId, consented ->
            list.firstOrNull { it.id == activeId }
                .toSettings(consented)
        }

    /** 写入后立即发射，便于测试断言 store.profiles.first()。 */
    fun seed(profile: AiProfile, isActive: Boolean = false) {
        _profiles.value = _profiles.value.filterNot { it.id == profile.id } + profile
        if (isActive) _activeId.value = profile.id
    }

    override suspend fun saveProfile(profile: AiProfile) {
        val current = _profiles.value
        _profiles.value =
            if (current.any { it.id == profile.id }) current.map { if (it.id == profile.id) profile else it }
            else current + profile
        // 与真实实现一致：无生效档案时自动激活首个已配置档案。
        if (_activeId.value == null) {
            val candidate = if (profile.isConfigured) profile else _profiles.value.firstOrNull { it.isConfigured }
            if (candidate != null) _activeId.value = candidate.id
        }
    }

    override suspend fun deleteProfile(profileId: String): Boolean {
        val target = _profiles.value.firstOrNull { it.id == profileId } ?: return false
        if (target.isPreset) return false
        _profiles.value = _profiles.value - target
        if (_activeId.value == profileId) _activeId.value = null
        keys.remove(profileId)
        return true
    }

    override suspend fun setActiveProfile(profileId: String?) {
        if (profileId == null) {
            _activeId.value = null
            return
        }
        if (_profiles.value.any { it.id == profileId }) _activeId.value = profileId
    }

    override suspend fun setApiKey(profileId: String, raw: String?) {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) keys.remove(profileId) else keys[profileId] = trimmed
    }

    override suspend fun hasApiKey(profileId: String): Boolean = keys.containsKey(profileId)

    override suspend fun apiKey(profileId: String): String? = keys[profileId]

    override suspend fun apiKey(): String? = _activeId.value?.let { keys[it] }

    override suspend fun setConsented(value: Boolean) {
        _consented.value = value
    }

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
}
