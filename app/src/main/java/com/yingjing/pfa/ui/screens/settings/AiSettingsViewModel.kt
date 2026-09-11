package com.yingjing.pfa.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.ai.AiChatMessage
import com.yingjing.pfa.data.ai.AiChatRequest
import com.yingjing.pfa.data.ai.AiApiProtocol
import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.data.ai.AiRemote
import com.yingjing.pfa.data.ai.AiReportTone
import com.yingjing.pfa.data.ai.AiSettingsStore
import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiFailureKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 档案编辑页状态。API Key 明文绝不进 state——只有「是否已保存」+ 尾号 4 位掩码；
 * 输入框里的临时明文放在 Compose 本地状态，保存后由 [save] 接收并立即丢弃。
 */
data class AiSettingsUiState(
    /** 编辑中的档案表单（预设档案 providerId 固定，自定义档案可改 name/模板）。 */
    val profile: AiProfile = AiProfile(id = "", providerId = AiProviderPreset.CUSTOM.id),
    /** true = 预设档案（隐藏名称输入与 provider chips）。 */
    val isPreset: Boolean = false,
    /** true = 编辑目标是尚未落盘的新建自定义档案（删除按钮不出现）。 */
    val isNew: Boolean = false,
    val loaded: Boolean = false,
    val hasKey: Boolean = false,
    val keyTail: String? = null,
    /** 状态标识（非最终文案）：UI 按枚举映射多语言资源。 */
    val status: AiStatus? = null,
    /** 服务商返回的原始错误说明（仅 BAD_REQUEST_DETAIL 有值），随状态一并展示。 */
    val statusDetail: String? = null,
    val saving: Boolean = false,
    val testing: Boolean = false,
)

/**
 * AI 档案编辑页 ViewModel（原单配置页改造）。路由 `settings_ai_edit/{profileId}`：
 * profileId = 档案 id 或 `new`（新建自定义，落盘时生成 `custom_<uuid>`）。
 *
 * 配置走 [AiSettingsStore]（DataStore 档案列表），API Key 按档案 id 加密写入数据库。
 * 保存不自动切换生效档案（改 key ≠ 要启用）；无任何生效档案时 Store 会自动激活首个已配置档案。
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiSettingsStore: AiSettingsStore,
    private val aiRemote: AiRemote,
) : ViewModel() {

    /** 编辑目标档案 id；[NEW_PROFILE_ID] 表示新建自定义（保存时才生成正式 id）。 */
    private val profileId: String = savedStateHandle.get<String>(ARG_PROFILE_ID) ?: NEW_PROFILE_ID

    private val _uiState = MutableStateFlow(AiSettingsUiState(isNew = profileId == NEW_PROFILE_ID))
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (profileId == NEW_PROFILE_ID) {
                _uiState.update { it.copy(loaded = true) }
            } else {
                val saved = aiSettingsStore.profiles.first().firstOrNull { it.id == profileId }
                if (saved == null) {
                    // 档案已被删除（例如在列表页删除后按返回回到本页）：置空标记，UI 引导返回。
                    _uiState.update { it.copy(loaded = true, status = AiStatus.NOT_CONFIGURED) }
                } else {
                    // 空预设档案预填服务商 defaults，免去手敲；已填字段原样保留。
                    val prefilled = if (saved.isPreset && saved.baseUrl.isBlank() && saved.model.isBlank()) {
                        saved.copy(
                            baseUrl = saved.provider.defaultBaseUrl,
                            model = saved.provider.defaultModel,
                        )
                    } else {
                        saved
                    }
                    _uiState.update {
                        it.copy(
                            profile = prefilled,
                            isPreset = saved.isPreset,
                            loaded = true,
                            hasKey = aiSettingsStore.hasApiKey(saved.id),
                            keyTail = savedKeyTail(saved.id),
                        )
                    }
                }
            }
        }
    }

    private suspend fun savedKeyTail(id: String): String? =
        aiSettingsStore.apiKey(id)?.takeIf { it.length >= 4 }?.takeLast(4)

    /** 当前表单对应的目标档案 id：新建档案在保存时生成（见 [save]）。 */
    private fun targetProfileId(state: AiSettingsUiState): String =
        if (state.isNew) AiProfile.newCustomId() else state.profile.id

    /** 自定义档案改显示名。 */
    fun setName(value: String) =
        _uiState.update { it.copy(profile = it.profile.copy(name = value.trim()), status = null, statusDetail = null) }

    /**
     * 选择服务商预设：自定义档案仅作模板回填默认 baseUrl / model（仍可手改）；
     * 预设档案不会走到这里（UI 不渲染 chips）。
     */
    fun setProvider(provider: AiProviderPreset) {
        _uiState.update {
            it.copy(
                profile = it.profile.copy(
                    providerId = provider.id,
                    baseUrl = provider.defaultBaseUrl,
                    model = provider.defaultModel,
                ),
                status = null,
                statusDetail = null,
            )
        }
    }

    fun setBaseUrl(value: String) =
        _uiState.update { it.copy(profile = it.profile.copy(baseUrl = value.trim()), status = null, statusDetail = null) }

    fun setModel(value: String) =
        _uiState.update { it.copy(profile = it.profile.copy(model = value.trim()), status = null, statusDetail = null) }

    /** 切换接口协议（Chat Completions / Responses），服务商支持的端点不同。 */
    fun setProtocol(value: AiApiProtocol) =
        _uiState.update { it.copy(profile = it.profile.copy(protocol = value), status = null, statusDetail = null) }

    /** 切换报告/分析输出语气档（分析师 / 伙伴）。 */
    fun setTone(value: AiReportTone) =
        _uiState.update { it.copy(profile = it.profile.copy(tone = value)) }

    /**
     * 设置单次生成输出 token 上限（文本输入）：空白/非数字回默认；上限裁到
     * [MAX_TOKENS_LIMIT]（防误填过大值触发服务商 4xx，如 0/负数也按非法回默认）。
     */
    fun setMaxTokens(input: String) {
        val value = input.trim().toIntOrNull()?.takeIf { it > 0 } ?: AiChatRequest.DEFAULT_MAX_TOKENS
        _uiState.update {
            it.copy(
                profile = it.profile.copy(maxTokens = value.coerceAtMost(MAX_TOKENS_LIMIT)),
                status = null,
                statusDetail = null,
            )
        }
    }

    fun setIncludeDetails(value: Boolean) =
        _uiState.update { it.copy(profile = it.profile.copy(includeDetails = value)) }

    /** 保存档案；[apiKeyInput] 非空时一并按档案 id 加密写入数据库（仅此一处接触明文）。 */
    fun save(apiKeyInput: String) {
        val state = _uiState.value
        if (!state.profile.isConfigured || state.saving) return
        _uiState.update { it.copy(saving = true, status = null) }
        viewModelScope.launch {
            val id = targetProfileId(state)
            val key = apiKeyInput.trim()
            if (key.isNotEmpty()) aiSettingsStore.setApiKey(id, key)
            aiSettingsStore.saveProfile(state.profile.copy(id = id))
            _uiState.update {
                it.copy(
                    profile = it.profile.copy(id = id),
                    isNew = false,
                    saving = false,
                    hasKey = aiSettingsStore.hasApiKey(id),
                    keyTail = savedKeyTail(id),
                    status = AiStatus.CONFIG_SAVED,
                    statusDetail = null,
                )
            }
        }
    }

    /** 清除本档案已保存的 API Key（其余配置保留）。 */
    fun clearKey() {
        val state = _uiState.value
        if (state.isNew) return
        viewModelScope.launch {
            aiSettingsStore.setApiKey(state.profile.id, null)
            _uiState.update { it.copy(hasKey = false, keyTail = null, status = AiStatus.KEY_CLEARED, statusDetail = null) }
        }
    }

    /** 删除本自定义档案（预设档案不可删；新建未落盘档案直接返回由 UI popBack）。 */
    fun deleteProfile(onResult: (Boolean) -> Unit) {
        val state = _uiState.value
        if (state.isNew) {
            onResult(true)
            return
        }
        if (state.isPreset) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val removed = aiSettingsStore.deleteProfile(state.profile.id)
            onResult(removed)
        }
    }

    /** 用当前表单配置 + 已保存 key（或本次输入的 key，提交前先暂存到本档案）发一条极短消息验证连通性。 */
    fun testConnection(apiKeyInput: String) {
        val state = _uiState.value
        if (!state.profile.isConfigured) {
            _uiState.update { it.copy(status = AiStatus.NOT_CONFIGURED, statusDetail = null) }
            return
        }
        if (state.testing) return
        _uiState.update { it.copy(testing = true, status = null, statusDetail = null) }
        viewModelScope.launch {
            val id = targetProfileId(state)
            val trimmedInput = apiKeyInput.trim()
            val apiKey = if (trimmedInput.isNotEmpty()) {
                // 测试时把刚输入未保存的 key 一并落盘（按目标档案 id），行为与「先保存再测试」一致。
                aiSettingsStore.setApiKey(id, trimmedInput)
                trimmedInput
            } else {
                aiSettingsStore.apiKey(id)
            }
            if (apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(testing = false, status = AiStatus.NO_KEY, statusDetail = null) }
                return@launch
            }
            val result = aiRemote.complete(
                AiChatRequest(
                    baseUrl = state.profile.baseUrl,
                    apiKey = apiKey,
                    model = state.profile.model,
                    messages = listOf(AiChatMessage(role = "user", content = PING_MESSAGE)),
                    protocol = state.profile.protocol,
                    maxTokens = PING_MAX_TOKENS,
                ),
            )
            _uiState.update {
                when (result) {
                    is AiChatResult.Success -> it.copy(
                        testing = false,
                        status = AiStatus.TEST_OK,
                        hasKey = true,
                        keyTail = apiKey.takeLast(4),
                    )
                    is AiChatResult.Failure -> it.copy(
                        testing = false,
                        status = AiStatus.fromFailure(result),
                        statusDetail = result.detail,
                    )
                }
            }
        }
    }

    /** 状态消息消费标记（UI 展示后调用清空，避免旋转屏幕后重复弹出）。 */
    fun consumeStatus() = _uiState.update { it.copy(status = null) }

    companion object {
        const val ARG_PROFILE_ID = "profileId"

        /** 路由占位：新建自定义档案。 */
        const val NEW_PROFILE_ID = "new"

        private const val PING_MESSAGE = "ping"

        // 混合推理模型（如腾讯 hy3）的思考 token 也计入输出预算且常占 90%+：
        // 预算太小（ Responses 协议翻倍后仍只有 16）会只产出 reasoning、正文为空，
        // 被误判为「服务商拒绝请求」。实测 512 预算即可完成一次 ping，取 1024 留余量。
        private const val PING_MAX_TOKENS = 1024

        /** 档案 max_tokens 输入上限：主流服务商上限 32k~128k，64k 已远超单份报告所需。 */
        const val MAX_TOKENS_LIMIT = 65536
    }
}

/** 状态标识：UI 侧映射为多语言文案（ai_config_saved / ai_err_* 等资源键）。 */
enum class AiStatus {
    CONFIG_SAVED,
    KEY_CLEARED,
    NOT_CONFIGURED,
    NO_KEY,
    TEST_OK,
    NETWORK,
    TIMEOUT,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER_ERROR,
    BAD_REQUEST,
    BAD_REQUEST_DETAIL,
    EMPTY_RESPONSE,
    ;

    companion object {
        fun fromFailure(failure: AiChatResult.Failure): AiStatus = when (failure.kind) {
            AiFailureKind.NETWORK -> NETWORK
            AiFailureKind.TIMEOUT -> TIMEOUT
            AiFailureKind.UNAUTHORIZED -> UNAUTHORIZED
            AiFailureKind.RATE_LIMITED -> RATE_LIMITED
            AiFailureKind.SERVER_ERROR -> SERVER_ERROR
            AiFailureKind.BAD_REQUEST -> BAD_REQUEST_DETAIL
            // 200 但无正文（推理模型思考吃光预算等）：不能落进 BAD_REQUEST 的通用文案误导排查。
            AiFailureKind.EMPTY_RESPONSE -> EMPTY_RESPONSE
            else -> BAD_REQUEST
        }
    }
}
