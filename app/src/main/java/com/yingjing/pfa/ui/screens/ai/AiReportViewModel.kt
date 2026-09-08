package com.yingjing.pfa.ui.screens.ai

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.R
import com.yingjing.pfa.data.ai.AiSettingsStore
import com.yingjing.pfa.data.local.AiReportRecordEntity
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.ai.AiAssistant
import com.yingjing.pfa.domain.ai.AiFailureKind
import com.yingjing.pfa.domain.ai.AiStreamEvent
import com.yingjing.pfa.domain.model.AiReportRecord
import com.yingjing.pfa.domain.repository.AiReportRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** AI 报告/分析页共享的界面状态机。 */
sealed interface AiUiState {
    data object Idle : AiUiState

    data object Loading : AiUiState

    /** 流式生成中：[markdown] 为已到达正文增量的累积，UI 实时渲染。 */
    data class Generating(
        val markdown: String,
        val model: String?,
    ) : AiUiState

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
 * AI 报告页 ViewModel：生成（可取消）/ 重新生成 / 导出 Markdown / 隐私同意 / 历史记录。
 *
 * 报告内容只在 VM 内存持有；生成成功后自动保存到本机记录库（SQLCipher，不外发），
 * 导出经 SAF 由调用方传入 uri。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiAssistant: AiAssistant,
    private val settingsStore: AiSettingsStore,
    private val recordRepository: AiReportRecordRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    /** 历史记录（按生成时间从新到旧），随库变化自动刷新。 */
    val history: StateFlow<List<AiReportRecord>> = sessionManager.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                recordRepository.observe(userId, AiReportRecordEntity.KIND_REPORT)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 正在请求删除确认的记录 id；null 表示无待确认项。 */
    private val _pendingDelete = MutableStateFlow<Long?>(null)
    val pendingDelete: StateFlow<Long?> = _pendingDelete.asStateFlow()

    private val _deletedHint = MutableStateFlow(false)
    val deletedHint: StateFlow<Boolean> = _deletedHint.asStateFlow()

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

    /** 生成（或重新生成）报告；流式接收增量并实时更新状态；进行中重复点击忽略。 */
    fun generate() {
        if (generateJob?.isActive == true) return
        _cancelHint.value = AiCancelHint.NONE
        _uiState.value = AiUiState.Loading
        generateJob = viewModelScope.launch {
            val accumulated = StringBuilder()
            var model: String? = null
            try {
                aiAssistant.reportStream().collect { event ->
                    when (event) {
                        is AiStreamEvent.Delta -> {
                            accumulated.append(event.text)
                            _uiState.value = AiUiState.Generating(accumulated.toString(), model)
                        }
                        is AiStreamEvent.Model -> {
                            model = event.name
                            val current = _uiState.value
                            if (current is AiUiState.Generating) {
                                _uiState.value = current.copy(model = model)
                            }
                        }
                        AiStreamEvent.Completed -> completeGeneration(accumulated.toString(), model)
                        is AiStreamEvent.Failed -> _uiState.value = AiUiState.Error(
                            kind = event.kind,
                            detail = event.detail,
                            canRetry = event.kind !in NO_RETRY_KINDS,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // remote 已把网络/超时归一为 Failed 事件；此处兜底收集过程的意外异常
                _uiState.value = AiUiState.Error(AiFailureKind.NETWORK, e.message, canRetry = true)
            }
        }
    }

    /** 流正常结束：正文为空按空响应报错；否则自动保存并进入 Done。 */
    private suspend fun completeGeneration(accumulated: String, model: String?) {
        if (accumulated.isBlank()) {
            _uiState.value = AiUiState.Error(AiFailureKind.EMPTY_RESPONSE, null, canRetry = true)
            return
        }
        val generatedAtMs = System.currentTimeMillis()
        saveRecord(accumulated, model, generatedAtMs)
        _uiState.value = AiUiState.Done(
            markdown = accumulated,
            generatedAtMs = generatedAtMs,
            model = model,
        )
    }

    /** 生成成功后自动保存（标题存本地日期 yyyy-MM-dd，前缀由 UI 按语言拼接）；失败不影响结果展示。 */
    private suspend fun saveRecord(markdown: String, model: String?, generatedAtMs: Long) {
        val userId = sessionManager.currentUserId.first() ?: return
        runCatching {
            recordRepository.save(
                userId = userId,
                kind = AiReportRecordEntity.KIND_REPORT,
                title = reportDate(generatedAtMs),
                model = model,
                markdown = markdown,
                createdAt = generatedAtMs,
            )
        }
    }

    /** 打开（回放）一条历史记录到 Done 态；生成进行中或记录已删除（id 无效）时忽略。 */
    fun openRecord(recordId: Long) {
        if (generateJob?.isActive == true) return
        viewModelScope.launch {
            recordRepository.getById(recordId)?.let { record ->
                _uiState.value = AiUiState.Done(
                    markdown = record.markdown,
                    generatedAtMs = record.createdAt,
                    model = record.model,
                )
            }
        }
    }

    /** 请求删除 [recordId]（UI 弹确认框后调 [confirmDelete]）。 */
    fun requestDelete(recordId: Long) {
        _pendingDelete.value = recordId
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    /** 确认删除待删记录；完成后短暂置位 [deletedHint] 供 UI 弹提示。 */
    fun confirmDelete() {
        val recordId = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            runCatching { recordRepository.delete(recordId) }
                .onSuccess { _deletedHint.value = true }
        }
    }

    fun clearDeletedHint() {
        _deletedHint.value = false
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

/** 生成时间 → 本地日期 yyyy-MM-dd，用于记录标题。 */
internal fun reportDate(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))

/**
 * [AiUiState.Error.kind] → 通用文案资源；独立顶层函数便于测试。
 *
 * BAD_REQUEST 的服务商原始说明经 [errorDetailText] 拼接展示，本函数只给兜底文案。
 */
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

/** 错误主文案：BAD_REQUEST 且有服务商原始说明时拼入 detail（Remote 已按 locale 优先 message_zh）。 */
@Composable
internal fun errorTextOf(kind: AiFailureKind, detail: String?): String =
    if (kind == AiFailureKind.BAD_REQUEST && !detail.isNullOrBlank()) {
        stringResource(R.string.ai_err_bad_request, detail)
    } else {
        stringResource(errorResOf(kind))
    }

/** 生成时间戳格式化（yyyy-MM-dd HH:mm，本地时区）；独立顶层函数便于测试。 */
internal fun formatGeneratedAt(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
