package com.yingjing.pfa.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.ai.AiSettingsStore
import com.yingjing.pfa.data.local.AiReportRecordEntity
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.ai.AiAssistant
import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiFailureKind
import com.yingjing.pfa.domain.model.AiReportRecord
import com.yingjing.pfa.domain.repository.AiReportRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import javax.inject.Inject

/**
 * AI 持仓分析页 ViewModel：生成（可取消）/ 重新生成 / 可选追问 / 隐私同意 / 历史记录。
 *
 * 与 [AiReportViewModel] 同状态机但不做导出；[question] 透传给分析模板
 * （空白视为未填），由 [AiAssistant.insight] 走 insightMessages。
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
    val consented: StateFlow<Boolean> = settingsStore.settings
        .map { it.consented }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var generateJob: Job? = null

    /** 用户在隐私对话框点「同意并继续」。 */
    fun consent() {
        viewModelScope.launch {
            val current = settingsStore.settings.first()
            settingsStore.save(current.copy(consented = true))
        }
    }

    /**
     * 生成（或重新生成）分析；进行中重复点击忽略。
     * [question] 用户可选的聚焦问题，trim 后空白视为未填。
     */
    fun generate(question: String? = null) {
        if (generateJob?.isActive == true) return
        _cancelHint.value = AiCancelHint.NONE
        _uiState.value = AiUiState.Loading
        val trimmed = question?.trim()?.takeIf { it.isNotEmpty() }
        generateJob = viewModelScope.launch {
            when (val result = aiAssistant.insight(trimmed)) {
                is AiChatResult.Success -> {
                    val generatedAtMs = System.currentTimeMillis()
                    saveRecord(result, generatedAtMs)
                    _uiState.value = AiUiState.Done(
                        markdown = result.text,
                        generatedAtMs = generatedAtMs,
                        model = result.model,
                    )
                }
                is AiChatResult.Failure -> _uiState.value = AiUiState.Error(
                    kind = result.kind,
                    detail = result.detail,
                    canRetry = result.kind !in NO_RETRY_KINDS,
                )
            }
        }
    }

    /** 生成成功后自动保存（标题存本地日期 yyyy-MM-dd）；失败不影响生成结果展示。 */
    private suspend fun saveRecord(result: AiChatResult.Success, generatedAtMs: Long) {
        val userId = sessionManager.currentUserId.first() ?: return
        runCatching {
            recordRepository.save(
                userId = userId,
                kind = AiReportRecordEntity.KIND_INSIGHT,
                title = reportDate(generatedAtMs),
                model = result.model,
                markdown = result.text,
                createdAt = generatedAtMs,
            )
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
