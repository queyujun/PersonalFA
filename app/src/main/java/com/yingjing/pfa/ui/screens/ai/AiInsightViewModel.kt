package com.yingjing.pfa.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.ai.AiSettingsStore
import com.yingjing.pfa.data.local.AiReportRecordEntity
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.ai.AiAssistant
import com.yingjing.pfa.domain.ai.AiFailureKind
import com.yingjing.pfa.domain.ai.AiStreamEvent
import com.yingjing.pfa.domain.model.AiReportRecord
import com.yingjing.pfa.domain.repository.AiReportRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 持仓分析页 ViewModel：生成（可取消）/ 重新生成 / 可选追问 / 隐私同意 / 历史记录。
 *
 * 与 [AiReportViewModel] 同状态机但不做导出；[question] 透传给分析模板
 * （空白视为未填），由 [AiAssistant.insightStream] 走 insightMessages。
 * 生成成功后自动保存到本机记录库（SQLCipher，不外发）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiInsightViewModel @Inject constructor(
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
                recordRepository.observe(userId, AiReportRecordEntity.KIND_INSIGHT)
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
    val consented: StateFlow<Boolean> = settingsStore.consented
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var generateJob: Job? = null

    /** 用户在隐私对话框点「同意并继续」（全局同意，不随档案切换重置）。 */
    fun consent() {
        viewModelScope.launch {
            settingsStore.setConsented(true)
        }
    }

    /**
     * 生成（或重新生成）分析；流式接收增量并实时更新状态；进行中重复点击忽略。
     * [question] 用户可选的聚焦问题，trim 后空白视为未填。
     */
    fun generate(question: String? = null) {
        if (generateJob?.isActive == true) return
        _cancelHint.value = AiCancelHint.NONE
        _uiState.value = AiUiState.Loading
        val trimmed = question?.trim()?.takeIf { it.isNotEmpty() }
        generateJob = viewModelScope.launch {
            val accumulated = StringBuilder()
            var model: String? = null
            try {
                aiAssistant.insightStream(trimmed).collect { event ->
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
                        is AiStreamEvent.Completed -> completeGeneration(accumulated.toString(), model, event.truncated)
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

    /** 流正常结束：正文为空按空响应报错；否则自动保存并进入 Done（[truncated] 时 UI 明示截断）。 */
    private suspend fun completeGeneration(accumulated: String, model: String?, truncated: Boolean) {
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
            truncated = truncated,
        )
    }

    /** 生成成功后自动保存（标题存本地日期 yyyy-MM-dd）；失败不影响生成结果展示。 */
    private suspend fun saveRecord(markdown: String, model: String?, generatedAtMs: Long) {
        val userId = sessionManager.currentUserId.first() ?: return
        runCatching {
            recordRepository.save(
                userId = userId,
                kind = AiReportRecordEntity.KIND_INSIGHT,
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

    override fun onCleared() {
        generateJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** 这两类错误引导用户去设置页修复，原地重试无意义。 */
        private val NO_RETRY_KINDS = setOf(AiFailureKind.NOT_CONFIGURED, AiFailureKind.NO_KEY)
    }
}
