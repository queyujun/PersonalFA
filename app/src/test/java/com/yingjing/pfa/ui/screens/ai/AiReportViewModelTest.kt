package com.yingjing.pfa.ui.screens.ai

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.ai.AiChatRequest
import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.data.ai.AiRemote
import com.yingjing.pfa.data.local.AiReportRecordEntity
import com.yingjing.pfa.domain.ai.AiAssistant
import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiFailureKind
import com.yingjing.pfa.domain.ai.AiStreamEvent
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.NetWorthPoint
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.fakes.FakeAiRemote
import com.yingjing.pfa.fakes.FakeAiReportRecordRepository
import com.yingjing.pfa.fakes.FakeAiSettingsStore
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
 * AiReportViewModel 状态机测试：Idle→Loading→Done、失败映射与 canRetry、
 * 生成中取消回 Idle、未配置不发请求、隐私同意持久化、导出结果。
 *
 * - Main 用 [UnconfinedTestDispatcher]：generate() 的 launch 内联执行到首个挂起点，
 *   成功路径同步完成；取消路径靠 [GatedRemote] 挂起在 gate 上。
 * - 运行在 AndroidJUnit4（Robolectric）下：Context 供 contentResolver 导出。
 * - 仓储用 fakes 包内存版 + [fx]/[snapshots] 内联替身（后者无现成 fake）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiReportViewModelTest {

    private val store = FakeAiSettingsStore()
    private val remote = FakeAiRemote()
    private val session = FakeSessionManager()
    private val users = FakeUserRepository()
    private val holdingsRepo = FakeHoldingRepository()
    private val recordsRepo = FakeAiReportRecordRepository()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun configuredStore() {
        val id = AiProfile.presetIdOf(AiProviderPreset.DEEPSEEK)
        store.seed(
            AiProfile(
                id = id,
                providerId = AiProviderPreset.DEEPSEEK.id,
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-chat",
            ),
            isActive = true,
        )
        store.setApiKey(id, "sk-test-key-8888")
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

    private fun viewModel(aiRemote: AiRemote = remote) = AiReportViewModel(
        context = ApplicationProvider.getApplicationContext<Context>(),
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
        recordRepository = recordsRepo,
        sessionManager = session,
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
        // 模型名透传自配置（API Key 只进 Authorization 头，不进 prompt，由 Remote 层保证）
        assertEquals("deepseek-chat", remote.requests.single().model)
    }

    @Test
    fun generate_success_autoSavesRecord_withDateTitle() = runTest {
        configuredStore()
        seedUserAndHolding()
        val vm = viewModel()
        vm.generate()
        assertTrue(vm.uiState.value is AiUiState.Done)

        // 自动保存：kind=report、标题为本地日期 yyyy-MM-dd、内容与模型透传
        val record = recordsRepo.records.single()
        assertEquals(1L, record.userId)
        assertEquals(AiReportRecordEntity.KIND_REPORT, record.kind)
        assertTrue("标题应为 yyyy-MM-dd", record.title.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals("ok", record.markdown)
        assertEquals("deepseek-chat", record.model)
        assertEquals(record.createdAt, (vm.uiState.value as AiUiState.Done).generatedAtMs)
    }

    @Test
    fun generate_success_withoutUser_doesNotSave() = runTest {
        configuredStore()
        // 不 seedUserAndHolding：未登录时 AiAssistant 直接返回 NO_USER，不应落库
        val vm = viewModel()
        vm.generate()
        val error = vm.uiState.value as AiUiState.Error
        assertEquals(AiFailureKind.NO_USER, error.kind)
        assertTrue(recordsRepo.records.isEmpty())
    }

    @Test
    fun generate_failure_doesNotSaveRecord() = runTest {
        configuredStore()
        seedUserAndHolding()
        remote.streams += listOf(AiStreamEvent.Failed(AiFailureKind.RATE_LIMITED))
        val vm = viewModel()
        vm.generate()
        assertTrue(vm.uiState.value is AiUiState.Error)
        assertTrue(recordsRepo.records.isEmpty())
    }

    @Test
    fun delete_confirmRemovesRecord_andCancelKeeps() = runTest {
        configuredStore()
        seedUserAndHolding()
        val vm = viewModel()
        vm.generate()
        val recordId = recordsRepo.records.single().id

        // 取消：不删
        vm.requestDelete(recordId)
        assertEquals(recordId, vm.pendingDelete.value)
        vm.cancelDelete()
        assertEquals(null, vm.pendingDelete.value)
        assertEquals(1, recordsRepo.records.size)

        // 确认：删除并置位提示
        vm.requestDelete(recordId)
        vm.confirmDelete()
        assertTrue(recordsRepo.records.isEmpty())
        assertEquals(null, vm.pendingDelete.value)
        assertTrue(vm.deletedHint.value)

        vm.clearDeletedHint()
        assertFalse(vm.deletedHint.value)
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
    fun generate_unauthorized_isRetryable() = runTest {
        configuredStore()
        seedUserAndHolding()
        remote.streams += listOf(AiStreamEvent.Failed(AiFailureKind.UNAUTHORIZED))
        val vm = viewModel()
        vm.generate()
        val error = vm.uiState.value as AiUiState.Error
        assertEquals(AiFailureKind.UNAUTHORIZED, error.kind)
        assertTrue(error.canRetry)
    }

    @Test
    fun generate_failure_then_fixedConfig_regenerate_succeeds() = runTest {
        configuredStore()
        seedUserAndHolding()
        remote.streams += listOf(AiStreamEvent.Failed(AiFailureKind.RATE_LIMITED))
        val vm = viewModel()
        vm.generate()
        assertTrue(vm.uiState.value is AiUiState.Error)

        // 队列耗尽后默认成功流：重试直达 Done
        vm.generate()
        assertTrue(vm.uiState.value is AiUiState.Done)
    }

    @Test
    fun generate_rendersDeltas_progressivelyBeforeCompletion() = runTest {
        configuredStore()
        seedUserAndHolding()
        val gated = GatedRemote()
        val vm = viewModel(gated)

        vm.generate()
        // 首段增量已到达：实时渲染为 Generating，尚未落库
        val generating = vm.uiState.value as AiUiState.Generating
        assertEquals("部分", generating.markdown)
        assertEquals("deepseek-chat", generating.model)
        assertTrue(recordsRepo.records.isEmpty())

        // 放行剩余增量 → 流完成 → Done 并自动保存全文
        gated.gate.complete(Unit)
        val done = vm.uiState.value as AiUiState.Done
        assertEquals("部分后文", done.markdown)
        assertEquals("deepseek-chat", done.model)
        assertEquals("部分后文", recordsRepo.records.single().markdown)
    }

    @Test
    fun cancel_duringGeneration_returnsToIdle_andIgnoresLateEvents() = runTest {
        configuredStore()
        seedUserAndHolding()
        val gated = GatedRemote()
        val vm = viewModel(gated)
        vm.generate()
        assertTrue(vm.uiState.value is AiUiState.Generating) // 首段增量已渲染，挂起在 gate 上

        vm.cancel()
        assertEquals(AiUiState.Idle, vm.uiState.value)
        assertEquals(AiCancelHint.JUST_CANCELLED, vm.cancelHint.value)

        // 迟到的增量/完成事件不覆盖取消后的状态（协程已取消，未落库）
        gated.gate.complete(Unit)
        assertEquals(AiUiState.Idle, vm.uiState.value)
        assertTrue(recordsRepo.records.isEmpty())
    }

    @Test
    fun consent_persists_toStore() = runTest {
        val vm = viewModel()
        assertFalse(vm.consented.value)
        vm.consent()
        assertTrue(store.settings.first().consented)
    }

    @Test
    fun openRecord_replaysRecord_toDone() = runTest {
        configuredStore()
        seedUserAndHolding()
        val vm = viewModel()
        vm.generate()
        val saved = recordsRepo.records.single()

        // 回到 Idle 后点击历史条目 → Done 且内容/时间/模型与记录一致
        vm.cancel()
        vm.clearCancelHint()
        vm.openRecord(saved.id)
        val done = vm.uiState.value as AiUiState.Done
        assertEquals(saved.markdown, done.markdown)
        assertEquals(saved.createdAt, done.generatedAtMs)
        assertEquals(saved.model, done.model)
    }

    @Test
    fun openRecord_unknownId_keepsCurrentState() = runTest {
        val vm = viewModel()
        vm.openRecord(999L)
        assertEquals(AiUiState.Idle, vm.uiState.value)
    }

    @Test
    fun exportTo_notDone_isNoOp() = runTest {
        val vm = viewModel()
        vm.exportTo(Uri.parse("content://test/unused"))
        assertEquals(AiExportResult.Idle, vm.exportResult.value)
    }

    @Test
    fun exportTo_success_reportsSuccess() = runTest {
        configuredStore()
        seedUserAndHolding()
        val vm = viewModel()
        vm.generate()
        assertTrue(vm.uiState.value is AiUiState.Done)

        // Robolectric 对未注册 uri 的 openOutputStream 返回可写内存流，写入会成功 → Success。
        vm.exportTo(Uri.parse("content://test/report.md"))
        assertTrue(vm.exportResult.value is AiExportResult.Success)
    }

    @Test
    fun errorResOf_coversAllKinds() {
        AiFailureKind.entries.forEach { kind ->
            assertTrue(errorResOf(kind) != 0)
        }
    }

    @Test
    fun generate_badRequest_errorCarriesProviderDetail() = runTest {
        configuredStore()
        seedUserAndHolding()
        remote.streams += listOf(
            AiStreamEvent.Failed(
                AiFailureKind.BAD_REQUEST,
                "输入的服务 ID 不存在，或模型与服务不匹配。",
            ),
        )
        val vm = viewModel()
        vm.generate()
        val error = vm.uiState.value as AiUiState.Error
        assertEquals(AiFailureKind.BAD_REQUEST, error.kind)
        assertEquals("输入的服务 ID 不存在，或模型与服务不匹配。", error.detail)
        assertTrue(error.canRetry)
    }

    /** 流式替身：发出首段增量后挂起在 [gate]，用于驱动渐进渲染与取消路径。 */
    private class GatedRemote : AiRemote {
        val gate = CompletableDeferred<Unit>()
        val requests = mutableListOf<AiChatRequest>()

        override suspend fun complete(request: AiChatRequest): AiChatResult =
            error("生成流程应走 stream 路径")

        override fun stream(request: AiChatRequest): Flow<AiStreamEvent> = flow {
            requests += request
            emit(AiStreamEvent.Model("deepseek-chat"))
            emit(AiStreamEvent.Delta("部分"))
            gate.await()
            emit(AiStreamEvent.Delta("后文"))
            emit(AiStreamEvent.Completed)
        }
    }
}
