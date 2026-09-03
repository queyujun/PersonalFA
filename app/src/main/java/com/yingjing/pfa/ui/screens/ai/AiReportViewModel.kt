package com.yingjing.pfa.ui.screens.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.R
import com.yingjing.pfa.data.ai.AiSettingsStore
import com.yingjing.pfa.domain.ai.AiAssistant
import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiFailureKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** AI 报告/分析页共享的界面状态机。 */
sealed interface AiUiState {
    data object Idle : AiUiState

    data object Loading : AiUiState

    /** 生成成功；[markdown] 仅内存持有，不落盘。[model] 用于「生成时间 · 模型」脚注。 */
    data class Done(
        val markdown: String,
        val generatedAtMs: Long,
        val model: String?,
    ) : AiUiState

    /** 生成失败；[canRetry] = 非「未配置/无 key」类错误（那两类引导去设置页）。 */
    data class Error(
        val kind: AiFailureKind,
        val detail: String? = null,
        val canRetry: Boolean,
    ) : AiUiState
}

/** 取消后回 Idle 的短暂提示标记；由 UI 消费后调 [AiReportViewModel.clearCancelHint] 复位。 */
enum class AiCancelHint { NONE, JUST_CANCELLED }

/** 导出结果：[Idle] 初始 / [Success] 写入成功 / [Failure] 携带可读错误信息。 */
sealed interface AiExportResult {
    data object Idle : AiExportResult
    data object Success : AiExportResult
    data class Failure(val message: String?) : AiExportResult
}

/**
 * AI 报告页 ViewModel：生成（可取消）/ 重新生成 / 导出 Markdown / 隐私同意。
 *
 * 报告内容只在 VM 内存持有（不落盘、不打日志）；导出经 SAF 由调用方传入 uri。
 */
@HiltViewModel
class AiReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiAssistant: AiAssistant,
    private val settingsStore: AiSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val _cancelHint = MutableStateFlow(AiCancelHint.NONE)
    val cancelHint: StateFlow<AiCancelHint> = _cancelHint.asStateFlow()

    /** 是否已同意隐私提示（DataStore 持久化）；未同意时首次生成前弹对话框。 */
    val consented: StateFlow<Boolean> = settingsStore.settings
        .map { it.consented }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _exportResult = MutableStateFlow<AiExportResult>(AiExportResult.Idle)
    val exportResult: StateFlow<AiExportResult> = _exportResult.asStateFlow()

    private var generateJob: Job? = null

    /** 用户在隐私对话框点「同意并继续」。 */
    fun consent() {
        viewModelScope.launch {
            val current = settingsStore.settings.first()
            settingsStore.save(current.copy(consented = true))
        }
    }

    /** 生成（或重新生成）报告；进行中重复点击忽略。 */
    fun generate() {
        if (generateJob?.isActive == true) return
        _cancelHint.value = AiCancelHint.NONE
        _uiState.value = AiUiState.Loading
        generateJob = viewModelScope.launch {
            when (val result = aiAssistant.report()) {
                is AiChatResult.Success -> _uiState.value = AiUiState.Done(
                    markdown = result.text,
                    generatedAtMs = System.currentTimeMillis(),
                    model = result.model,
                )
                is AiChatResult.Failure -> _uiState.value = AiUiState.Error(
                    kind = result.kind,
                    detail = result.detail,
                    canRetry = result.kind !in NO_RETRY_KINDS,
                )
            }
        }
    }

    /** 取消进行中的生成；用户主动取消不视为错误，回到 Idle 并提示。 */
    fun cancel() {
        generateJob?.cancel()
        generateJob = null
        _uiState.value = AiUiState.Idle
        _cancelHint.value = AiCancelHint.JUST_CANCELLED
    }

    fun clearCancelHint() {
        _cancelHint.value = AiCancelHint.NONE
    }

    /** 导出 Markdown 到 [uri]（SAF CreateDocument）；结果经 [exportResult] 回报。 */
    fun exportTo(uri: Uri) {
        val current = _uiState.value as? AiUiState.Done ?: return
        viewModelScope.launch {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(current.markdown.toByteArray(Charsets.UTF_8))
                } ?: error("cannot open output stream")
            }
            _exportResult.value = result.fold(
                onSuccess = { AiExportResult.Success },
                onFailure = { AiExportResult.Failure(it.message) },
            )
        }
    }

    fun clearExportResult() {
        _exportResult.value = AiExportResult.Idle
    }

    override fun onCleared() {
        generateJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** 这两类错误引导用户去设置页修复，原地重试无意义。 */
        private val NO_RETRY_KINDS = setOf(AiFailureKind.NOT_CONFIGURED, AiFailureKind.NO_KEY)
    }
}

/** [AiUiState.Error.kind] → 文案资源；独立顶层函数便于测试。 */
internal fun errorResOf(kind: AiFailureKind): Int = when (kind) {
    AiFailureKind.NOT_CONFIGURED -> R.string.ai_err_not_configured
    AiFailureKind.NO_KEY -> R.string.ai_err_no_key
    AiFailureKind.NO_USER -> R.string.ai_err_no_user
    AiFailureKind.NETWORK -> R.string.ai_err_network
    AiFailureKind.TIMEOUT -> R.string.ai_err_timeout
    AiFailureKind.UNAUTHORIZED -> R.string.ai_err_unauthorized
    AiFailureKind.RATE_LIMITED -> R.string.ai_err_rate_limited
    AiFailureKind.SERVER_ERROR -> R.string.ai_err_server
    AiFailureKind.BAD_REQUEST -> R.string.ai_err_bad_request_generic
    AiFailureKind.EMPTY_RESPONSE -> R.string.ai_err_empty_response
}

/** 生成时间戳格式化（yyyy-MM-dd HH:mm，本地时区）；独立顶层函数便于测试。 */
internal fun formatGeneratedAt(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
