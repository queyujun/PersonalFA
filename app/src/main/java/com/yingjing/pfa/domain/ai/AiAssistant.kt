package com.yingjing.pfa.domain.ai

import com.yingjing.pfa.data.ai.AiChatRequest
import com.yingjing.pfa.data.ai.AiRemote
import com.yingjing.pfa.data.ai.AiSettingsStore
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HoldingRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 助手门面：收集当前用户数据 → 构造脱敏 payload → 组装 prompt → 调用 OpenAI 兼容接口（流式）。
 *
 * 隐私约束（实现层保证）：
 * - payload 由 [PortfolioPayloadBuilder] 白名单构造，note/身份/时间戳不可能外发；
 * - API Key 只进 Authorization 头，绝不进 prompt；
 * - 结果仅返回给调用方（ViewModel 内存持有），此处不落盘不打日志。
 */
@Singleton
class AiAssistant @Inject constructor(
    private val sessionManager: SessionManager,
    private val holdingRepository: HoldingRepository,
    private val fxRepository: FxRepository,
    private val userRepository: UserRepository,
    private val snapshotRepository: SnapshotRepository,
    private val aiRemote: AiRemote,
    private val settingsStore: AiSettingsStore,
) {

    /** 生成个人资产报告（Markdown，流式增量事件）。 */
    suspend fun reportStream(): Flow<AiStreamEvent> = generateStream(withReportTemplate = true, question = null)

    /** 生成持仓分析（Markdown，流式增量事件）；[question] 为可选的用户追问。 */
    suspend fun insightStream(question: String? = null): Flow<AiStreamEvent> =
        generateStream(withReportTemplate = false, question)

    private suspend fun generateStream(withReportTemplate: Boolean, question: String?): Flow<AiStreamEvent> {
        val settings = settingsStore.settings.first()
        if (!settings.isConfigured) return flowOf(AiStreamEvent.Failed(AiFailureKind.NOT_CONFIGURED))
        val apiKey = settingsStore.apiKey() ?: return flowOf(AiStreamEvent.Failed(AiFailureKind.NO_KEY))

        val userId = sessionManager.currentUserId.first() ?: return flowOf(AiStreamEvent.Failed(AiFailureKind.NO_USER))
        val user = userRepository.getUser(userId) ?: return flowOf(AiStreamEvent.Failed(AiFailureKind.NO_USER))

        val holdings = holdingRepository.observeHoldingsSnapshot(userId)
        val rates = fxRepository.current()
        val displayCurrency = user.defaultCurrency

        val series = snapshotRepository.observe(userId).first()
            .sortedBy { it.epochDay }
            .map { it.netWorth }
            .takeIf { it.size >= 2 }

        val payload = PortfolioPayloadBuilder.build(
            holdings = holdings,
            rates = rates,
            displayCurrency = displayCurrency,
            nowMs = System.currentTimeMillis(),
            netWorthSeries = series,
            includeDetails = settings.includeDetails,
        )
        val payloadJson = PortfolioPayloadBuilder.toJson(payload)

        // 输出语言：跟随当前 App locale（per-app locale 生效后 Locale.getDefault 同步更新）
        val localeTag = Locale.getDefault().toLanguageTag()
        val messages = if (withReportTemplate) {
            AiPromptBuilder.reportMessages(payloadJson, localeTag)
        } else {
            AiPromptBuilder.insightMessages(payloadJson, localeTag, question)
        }

        return aiRemote.stream(
            AiChatRequest(
                baseUrl = settings.baseUrl,
                apiKey = apiKey,
                model = settings.model,
                messages = messages,
                protocol = settings.protocol,
            ),
        )
    }
}
