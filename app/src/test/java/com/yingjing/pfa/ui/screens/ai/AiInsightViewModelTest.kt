package com.yingjing.pfa.ui.screens.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.ai.AiChatRequest
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.data.ai.AiRemote
import com.yingjing.pfa.data.ai.AiSettings
import com.yingjing.pfa.domain.ai.AiAssistant
import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiFailureKind
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.NetWorthPoint
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.fakes.FakeAiRemote
import com.yingjing.pfa.fakes.FakeAiSettingsStore
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AiInsightViewModel 状态机测试：Idle→Loading→Done、追问透传与空白剔除、
 * 失败映射与 canRetry、生成中取消回 Idle、未配置不发请求、隐私同意持久化。
 *
 * - Main 用 [UnconfinedTestDispatcher]：generate() 的 launch 内联执行到首个挂起点，
 *   成功路径同步完成；取消路径靠 [GatedRemote] 挂起在 gate 上。
 * - 运行在 AndroidJUnit4（Robolectric）下，与 AiReportViewModelTest 同范式。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiInsightViewModelTest {

    private val store = FakeAiSettingsStore()
    private val remote = FakeAiRemote()
    private val session = FakeSessionManager()
    private val users = FakeUserRepository()
    private val holdingsRepo = FakeHoldingRepository()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun configuredStore() {
        store.save(
            AiSettings(
                providerId = AiProviderPreset.DEEPSEEK.id,
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-chat",
            ),
        )
        store.setApiKey("sk-test-key-8888")
    }

    /** 注册用户（id=1）+ 写入会话 + 一只 A 股持仓，保证 generate 能走到远程调用。 */
    private suspend fun seedUserAndHolding() {
        users.register("tester", "password-123", Currency.CNY)
        session.setCurrentUser(1L)
        holdingsRepo.addHolding(
            Holding(
                userId = 1,
                type = AssetType.A_SHARE,
                name = "贵州茅台",
                currency = Currency.CNY,
                symbol = "600519",
                quantity = 100.0,
                costPrice = 1650.0,
                currentPrice = 1680.0,
            ),
        )
    }

    private fun viewModel(aiRemote: AiRemote = remote) = AiInsightViewModel(
        aiAssistant = AiAssistant(
            sessionManager = session,
            holdingRepository = holdingsRepo,
            fxRepository = fx,
            userRepository = users,
            snapshotRepository = snapshots,
            aiRemote = aiRemote,
            settingsStore = store,
        ),
        settingsStore = store,
    )

    private val fx = object : FxRepository {
        override fun observeRates() = flowOf(FxRates(usdToCny = 7.0, hkdToCny = 0.9))
        override suspend fun current() = FxRates(usdToCny = 7.0, hkdToCny = 0.9)
        override suspend fun refresh() = FxRates(usdToCny = 7.0, hkdToCny = 0.9)
        override suspend fun save(rates: FxRates) {}
    }

    /** 净值序列固定两点（AiAssistant 要求 size≥2 才随 payload 携带趋势）。 */
    private val snapshots = object : SnapshotRepository {
        override fun observe(userId: Long): Flow<List<NetWorthPoint>> = flowOf(
            listOf(
                NetWorthPoint(epochDay = 20_000, netWorth = 168_000.0),
                NetWorthPoint(epochDay = 20_001, netWorth = 171_500.0),
            ),
        )

        override fun observeCategories(userId: Long): Flow<List<CategoryPoint>> =
            flowOf(emptyList())

        override suspend fun record(
            userId: Long,
            currency: Currency,
            totalAssets: Double,
            totalLiabilities: Double,
            netWorth: Double,
            categoryAmounts: Map<String, Double>,
            nowMs: Long,
        ) {}

        override suspend fun deleteBefore(userId: Long, dayEpochDay: Long) {}
    }

    @Test
    fun generate_success_mapsDone_andCarriesModel() = runTest {
        configuredStore()
        seedUserAndHolding()
        val vm = viewModel()
        assertEquals(AiUiState.Idle, vm.uiState.value)

        vm.generate()
        val done = vm.uiState.value as AiUiState.Done
        assertEquals("ok", done.markdown)
        assertEquals("deepseek-chat", done.model)
    }

    @Test
    fun generate_question_passesThroughToPrompt() = runTest {
        configuredStore()
        seedUserAndHolding()
        val vm = viewModel()

        vm.generate("港股持仓是否过于集中")
        vm.uiState.value as AiUiState.Done

        val messages = remote.requests.single().messages
        assertEquals(2, messages.size)
        assertTrue(
            "system 应为分析模板",
            messages[0].content.contains("portfolio insight review"),
        )
        assertTrue(
            "追问应进入 user 消息",
            messages[1].content.contains("港股持仓是否过于集中"),
        )
    }

    @Test
    fun generate_blankQuestion_treatedAsNoQuestion() = runTest {
        configuredStore()
        seedUserAndHolding()
        val vm = viewModel()

        vm.generate("   ")
        vm.uiState.value as AiUiState.Done

        val userContent = remote.requests.single().messages[1].content
        assertFalse(
            "空白追问不应进入 user 消息",
            userContent.contains("The user especially wants your view on"),
        )
    }

    @Test
    fun generate_question_trimmedAndTruncatedTo200() = runTest {
        configuredStore()
        seedUserAndHolding()
        val vm = viewModel()

        vm.generate("  ${"问".repeat(250)}  ")
        vm.uiState.value as AiUiState.Done

        val userContent = remote.requests.single().messages[1].content
        assertTrue(userContent.contains("问".repeat(200)))
        assertFalse(userContent.contains("问".repeat(201)))
        assertFalse(userContent.contains("  ${"问".repeat(200)}"))
    }

    @Test
    fun generate_notConfigured_errorWithoutRetry() = runTest {
        val vm = viewModel() // 未配置 baseUrl/model
        vm.generate()
        val error = vm.uiState.value as AiUiState.Error
        assertEquals(AiFailureKind.NOT_CONFIGURED, error.kind)
        assertFalse(error.canRetry)
        assertTrue(remote.requests.isEmpty())
    }

    @Test
    fun generate_rateLimited_isRetryable_thenRecovers() = runTest {
        configuredStore()
        seedUserAndHolding()
        remote.results += AiChatResult.Failure(AiFailureKind.RATE_LIMITED)
        val vm = viewModel()
        vm.generate()
        val error = vm.uiState.value as AiUiState.Error
        assertEquals(AiFailureKind.RATE_LIMITED, error.kind)
        assertTrue(error.canRetry)

        // 队列耗尽后默认成功：重试直达 Done
        vm.generate()
        assertTrue(vm.uiState.value is AiUiState.Done)
    }

    @Test
    fun cancel_duringGeneration_returnsToIdle_andIgnoresLateResult() = runTest {
        configuredStore()
        seedUserAndHolding()
        val gated = GatedRemote()
        val vm = viewModel(gated)
        vm.generate()
        assertTrue(vm.uiState.value is AiUiState.Loading) // 挂起在 gate 上

        vm.cancel()
        assertEquals(AiUiState.Idle, vm.uiState.value)
        assertEquals(AiCancelHint.JUST_CANCELLED, vm.cancelHint.value)

        // 迟到的结果不覆盖取消后的状态（协程已取消）
        gated.gate.complete(
            AiChatResult.Success(text = "late", model = "m", promptTokens = null, completionTokens = null),
        )
        assertEquals(AiUiState.Idle, vm.uiState.value)
    }

    @Test
    fun consent_persists_toStore() = runTest {
        val vm = viewModel()
        assertFalse(vm.consented.value)
        vm.consent()
        assertTrue(store.settings.first().consented)
    }

    /** complete 挂起直到 [gate] 完成，用于驱动取消路径。 */
    private class GatedRemote : AiRemote {
        val gate = CompletableDeferred<AiChatResult>()
        val requests = mutableListOf<AiChatRequest>()

        override suspend fun complete(request: AiChatRequest): AiChatResult {
            requests += request
            return gate.await()
        }
    }
}
