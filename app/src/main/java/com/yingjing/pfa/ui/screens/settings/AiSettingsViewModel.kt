package com.yingjing.pfa.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.ai.AiChatMessage
import com.yingjing.pfa.data.ai.AiChatRequest
import com.yingjing.pfa.data.ai.AiApiProtocol
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.data.ai.AiRemote
import com.yingjing.pfa.data.ai.AiSettings
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
 * AI 设置页状态。API Key 明文绝不进 state——只有「是否已保存」+ 尾号 4 位掩码；
 * 输入框里的临时明文放在 Compose 本地状态，保存后由 [save] 接收并立即丢弃。
 */
data class AiSettingsUiState(
    val settings: AiSettings = AiSettings(),
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
 * AI 配置二级页 ViewModel。独立于 [SettingsViewModel]（其依赖已多），
 * 配置走 [AiSettingsStore]（DataStore），API Key 由其转存 Keystore 加密文件。
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val aiSettingsStore: AiSettingsStore,
    private val aiRemote: AiRemote,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = aiSettingsStore.settings.first()
            _uiState.update {
                it.copy(settings = saved, hasKey = aiSettingsStore.hasApiKey(), keyTail = savedKeyTail())
            }
        }
    }

    private suspend fun savedKeyTail(): String? =
        aiSettingsStore.apiKey()?.takeIf { it.length >= 4 }?.takeLast(4)

    /** 选择服务商预设：回填该服务商默认 baseUrl / model（仍可手改）。 */
    fun setProvider(provider: AiProviderPreset) {
        _uiState.update {
            it.copy(
                settings = it.settings.copy(
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
        _uiState.update { it.copy(settings = it.settings.copy(baseUrl = value.trim()), status = null, statusDetail = null) }

    fun setModel(value: String) =
        _uiState.update { it.copy(settings = it.settings.copy(model = value.trim()), status = null, statusDetail = null) }

    /** 切换接口协议（Chat Completions / Responses），服务商支持的端点不同。 */
    fun setProtocol(value: AiApiProtocol) =
        _uiState.update { it.copy(settings = it.settings.copy(protocol = value), status = null, statusDetail = null) }

    fun setIncludeDetails(value: Boolean) =
        _uiState.update { it.copy(settings = it.settings.copy(includeDetails = value)) }

    /** 保存配置；[apiKeyInput] 非空时一并写入 Keystore 加密文件（仅此一处接触明文）。 */
    fun save(apiKeyInput: String) {
        val state = _uiState.value
        if (!state.settings.isConfigured || state.saving) return
        _uiState.update { it.copy(saving = true, status = null) }
        viewModelScope.launch {
            val key = apiKeyInput.trim()
            if (key.isNotEmpty()) aiSettingsStore.setApiKey(key)
            aiSettingsStore.save(state.settings)
            _uiState.update {
                it.copy(
                    saving = false,
                    hasKey = aiSettingsStore.hasApiKey(),
                    keyTail = savedKeyTail(),
                    status = AiStatus.CONFIG_SAVED,
                    statusDetail = null,
                )
            }
        }
    }

    /** 清除已保存的 API Key（其余配置保留）。 */
    fun clearKey() {
        viewModelScope.launch {
            aiSettingsStore.setApiKey(null)
            _uiState.update { it.copy(hasKey = false, keyTail = null, status = AiStatus.KEY_CLEARED, statusDetail = null) }
        }
    }

    /** 用当前表单配置 + 已保存 key（或本次输入的 key，提交前先暂存）发一条极短消息验证连通性。 */
    fun testConnection(apiKeyInput: String) {
        val state = _uiState.value
        if (!state.settings.isConfigured) {
            _uiState.update { it.copy(status = AiStatus.NOT_CONFIGURED, statusDetail = null) }
            return
        }
        if (state.testing) return
        _uiState.update { it.copy(testing = true, status = null, statusDetail = null) }
        viewModelScope.launch {
            val trimmedInput = apiKeyInput.trim()
            val apiKey = if (trimmedInput.isNotEmpty()) {
                // 测试时把刚输入未保存的 key 一并落盘，行为与「先保存再测试」一致。
                aiSettingsStore.setApiKey(trimmedInput)
                trimmedInput
            } else {
                aiSettingsStore.apiKey()
            }
            if (apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(testing = false, status = AiStatus.NO_KEY, statusDetail = null) }
                return@launch
            }
            val result = aiRemote.complete(
                AiChatRequest(
                    baseUrl = state.settings.baseUrl,
                    apiKey = apiKey,
                    model = state.settings.model,
                    messages = listOf(AiChatMessage(role = "user", content = PING_MESSAGE)),
                    protocol = state.settings.protocol,
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

    private companion object {
        const val PING_MESSAGE = "ping"

        // 混合推理模型（如腾讯 hy3）的思考 token 也计入输出预算且常占 90%+：
        // 预算太小（ Responses 协议翻倍后仍只有 16）会只产出 reasoning、正文为空，
        // 被误判为「服务商拒绝请求」。实测 512 预算即可完成一次 ping，取 1024 留余量。
        const val PING_MAX_TOKENS = 1024
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
