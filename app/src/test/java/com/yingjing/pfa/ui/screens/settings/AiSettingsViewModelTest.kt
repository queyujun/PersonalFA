package com.yingjing.pfa.ui.screens.settings

import com.yingjing.pfa.data.ai.AiApiProtocol
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.data.ai.AiSettings
import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiFailureKind
import com.yingjing.pfa.fakes.FakeAiRemote
import com.yingjing.pfa.fakes.FakeAiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AiSettingsViewModel 逻辑测试：预设回填、https 校验之外的保存/清除流程、
 * key 明文不进 state、测试连接的结果映射。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsViewModelTest {

    private val store = FakeAiSettingsStore()
    private val remote = FakeAiRemote()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AiSettingsViewModel(store, remote)

    @Test
    fun setProvider_fillsDefaults_butEditable() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.KIMI)
        val settings = vm.uiState.value.settings
        assertEquals(AiProviderPreset.KIMI.id, settings.providerId)
        assertEquals("https://api.moonshot.cn/v1", settings.baseUrl)
        assertEquals("moonshot-v1-32k", settings.model)

        // 手改后不被覆盖
        vm.setModel("my-model")
        assertEquals("my-model", vm.uiState.value.settings.model)
    }

    @Test
    fun setProvider_hunyuanAndDoubao_fillPresetUrls() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.HUNYUAN)
        with(vm.uiState.value.settings) {
            assertEquals("https://api.hunyuan.cloud.tencent.com/v1", baseUrl)
            assertEquals("hunyuan-turbos-latest", model)
        }

        // 豆包只回填 base URL：模型名须用户填方舟模型 ID / 接入点，预设不代填
        vm.setProvider(AiProviderPreset.DOUBAO)
        with(vm.uiState.value.settings) {
            assertEquals("https://ark.cn-beijing.volces.com/api/v3", baseUrl)
            assertEquals("", model)
            assertFalse(isConfigured) // 模型名未填前不视为已配置
        }
    }

    @Test
    fun save_persistsConfig_andKeyNeverEntersStateInPlaintext() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.DEEPSEEK)
        vm.save(apiKeyInput = "sk-secret-123456")

        assertEquals("sk-secret-123456", store.storedKey) // 落到了 Keystore 模拟层
        assertTrue(vm.uiState.value.hasKey)
        assertEquals("3456", vm.uiState.value.keyTail)   // 只有尾号掩码
        assertEquals(AiStatus.CONFIG_SAVED, vm.uiState.value.status)
        val stateJson = vm.uiState.value.toString()
        assertFalse("state 不应包含 key 明文", stateJson.contains("sk-secret-123456"))
    }

    @Test
    fun save_emptyInput_keepsExistingKey() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.DEEPSEEK)
        vm.save(apiKeyInput = "sk-first-key-0001")
        vm.save(apiKeyInput = "") // 不带新 key 再保存 → 原键保留

        assertEquals("sk-first-key-0001", store.storedKey)
    }

    @Test
    fun clearKey_removesKey_butKeepsConfig() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.DEEPSEEK)
        vm.save(apiKeyInput = "sk-abc-9999")
        vm.clearKey()

        assertNull(store.storedKey)
        assertFalse(vm.uiState.value.hasKey)
        assertNull(vm.uiState.value.keyTail)
        // 配置仍在
        assertTrue(vm.uiState.value.settings.isConfigured)
    }

    @Test
    fun save_notConfigured_isNoOp() = runTest {
        val vm = viewModel()
        vm.save(apiKeyInput = "sk-x") // 未选预设/未填 baseUrl → 忽略

        assertNull(store.storedKey)
        assertFalse(vm.uiState.value.saving)
    }

    @Test
    fun testConnection_success_mapsTestOk() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.DEEPSEEK)
        vm.save(apiKeyInput = "sk-live-key-7777")
        remote.results += AiChatResult.Success("pong", "deepseek-chat", null, null)

        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.TEST_OK, vm.uiState.value.status)
        assertFalse(vm.uiState.value.testing)
        assertEquals("https://api.deepseek.com/v1", remote.requests.single().baseUrl)
        assertEquals("Bearer 提取正确", "sk-live-key-7777", remote.requests.single().apiKey)
    }

    @Test
    fun testConnection_mapsFailureKinds() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.DEEPSEEK)
        vm.save(apiKeyInput = "sk-bad")

        remote.results += AiChatResult.Failure(AiFailureKind.UNAUTHORIZED)
        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.UNAUTHORIZED, vm.uiState.value.status)

        remote.results += AiChatResult.Failure(AiFailureKind.RATE_LIMITED)
        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.RATE_LIMITED, vm.uiState.value.status)

        remote.results += AiChatResult.Failure(AiFailureKind.TIMEOUT)
        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.TIMEOUT, vm.uiState.value.status)

        remote.results += AiChatResult.Failure(AiFailureKind.EMPTY_RESPONSE)
        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.EMPTY_RESPONSE, vm.uiState.value.status)

        remote.results += AiChatResult.Failure(AiFailureKind.BAD_REQUEST, "model not found")
        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.BAD_REQUEST_DETAIL, vm.uiState.value.status)
        assertEquals("model not found", vm.uiState.value.statusDetail)
    }

    @Test
    fun testConnection_withoutKey_reportsNoKey() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.DEEPSEEK)
        vm.save(apiKeyInput = "") // 只保存配置，未录 key

        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.NO_KEY, vm.uiState.value.status)
        assertTrue(remote.requests.isEmpty())
    }

    @Test
    fun testConnection_notConfigured_reportsNotConfigured() = runTest {
        val vm = viewModel()
        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.NOT_CONFIGURED, vm.uiState.value.status)
        assertTrue(remote.requests.isEmpty())
    }

    @Test
    fun init_loadsPersistedSettings() = runTest {
        store.save(AiSettings(providerId = AiProviderPreset.QWEN.id, baseUrl = "https://x/y", model = "m1"))
        store.setApiKey("sk-persisted-1234")

        val vm = viewModel()
        assertEquals(AiProviderPreset.QWEN.id, vm.uiState.value.settings.providerId)
        assertTrue(vm.uiState.value.hasKey)
        assertEquals("1234", vm.uiState.value.keyTail)
    }

    @Test
    fun setProtocol_updatesState_andPersistsThroughSave() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.HUNYUAN)
        vm.setProtocol(AiApiProtocol.RESPONSES)
        assertEquals(AiApiProtocol.RESPONSES, vm.uiState.value.settings.protocol)

        vm.save(apiKeyInput = "sk-proto-0001")
        assertEquals(AiApiProtocol.RESPONSES, store.settings.first().protocol)

        // 切回默认协议
        vm.setProtocol(AiApiProtocol.CHAT_COMPLETIONS)
        assertEquals(AiApiProtocol.CHAT_COMPLETIONS, vm.uiState.value.settings.protocol)
    }

    @Test
    fun testConnection_usesSelectedProtocol() = runTest {
        val vm = viewModel()
        vm.setProvider(AiProviderPreset.HUNYUAN)
        vm.setProtocol(AiApiProtocol.RESPONSES)
        vm.save(apiKeyInput = "sk-hy3-key-8888")
        remote.results += AiChatResult.Success("pong", "hy3", null, null)

        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.TEST_OK, vm.uiState.value.status)
        assertEquals(AiApiProtocol.RESPONSES, remote.requests.single().protocol)
    }
}
