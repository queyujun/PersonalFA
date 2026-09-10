package com.yingjing.pfa.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import com.yingjing.pfa.data.ai.AiApiProtocol
import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiProviderPreset
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AiSettingsViewModel（档案编辑页）逻辑测试：预设回填、按档案保存/清除 key、
 * key 明文不进 state、测试连接的结果映射、新建自定义档案与删除边界。
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

    private fun viewModel(profileId: String) =
        AiSettingsViewModel(
            SavedStateHandle(mapOf(AiSettingsViewModel.ARG_PROFILE_ID to profileId)),
            store,
            remote,
        )

    /** 造一个已落盘的预设档案并返回其 id。 */
    private fun seededPreset(
        provider: AiProviderPreset,
        baseUrl: String = provider.defaultBaseUrl,
        model: String = provider.defaultModel,
    ): String {
        val id = AiProfile.presetIdOf(provider)
        store.seed(AiProfile(id = id, providerId = provider.id, baseUrl = baseUrl, model = model))
        return id
    }

    @Test
    fun setProvider_fillsDefaults_butEditable() = runTest {
        val vm = viewModel(AiSettingsViewModel.NEW_PROFILE_ID)
        vm.setProvider(AiProviderPreset.KIMI)
        val profile = vm.uiState.value.profile
        assertEquals(AiProviderPreset.KIMI.id, profile.providerId)
        assertEquals("https://api.moonshot.cn/v1", profile.baseUrl)
        assertEquals("moonshot-v1-32k", profile.model)

        // 手改后不被覆盖
        vm.setModel("my-model")
        assertEquals("my-model", vm.uiState.value.profile.model)
    }

    @Test
    fun setProvider_hunyuanAndDoubao_fillPresetUrls() = runTest {
        val vm = viewModel(AiSettingsViewModel.NEW_PROFILE_ID)
        vm.setProvider(AiProviderPreset.HUNYUAN)
        with(vm.uiState.value.profile) {
            assertEquals("https://api.hunyuan.cloud.tencent.com/v1", baseUrl)
            assertEquals("hunyuan-turbos-latest", model)
        }

        // 豆包只回填 base URL：模型名须用户填方舟模型 ID / 接入点，预设不代填
        vm.setProvider(AiProviderPreset.DOUBAO)
        with(vm.uiState.value.profile) {
            assertEquals("https://ark.cn-beijing.volces.com/api/v3", baseUrl)
            assertEquals("", model)
            assertFalse(isConfigured) // 模型名未填前不视为已配置
        }
    }

    @Test
    fun init_presetProfile_prefillsProviderDefaults() = runTest {
        // 空预设档案（未填 baseUrl/model）打开时预填服务商 defaults，免去手敲。
        val id = seededPreset(AiProviderPreset.QWEN, baseUrl = "", model = "")
        val vm = viewModel(id)

        val profile = vm.uiState.value.profile
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", profile.baseUrl)
        assertEquals("qwen-plus", profile.model)
        assertTrue(vm.uiState.value.isPreset)
    }

    @Test
    fun save_persistsConfig_andKeyNeverEntersStateInPlaintext() = runTest {
        val id = seededPreset(AiProviderPreset.DEEPSEEK)
        val vm = viewModel(id)
        vm.save(apiKeyInput = "sk-secret-123456")

        assertEquals("sk-secret-123456", store.keys[id]) // 落到了按档案密钥层
        assertTrue(vm.uiState.value.hasKey)
        assertEquals("3456", vm.uiState.value.keyTail)   // 只有尾号掩码
        assertEquals(AiStatus.CONFIG_SAVED, vm.uiState.value.status)
        val stateJson = vm.uiState.value.toString()
        assertFalse("state 不应包含 key 明文", stateJson.contains("sk-secret-123456"))
    }

    @Test
    fun save_newCustomProfile_generatesId_andStoresPerProfileKey() = runTest {
        val vm = viewModel(AiSettingsViewModel.NEW_PROFILE_ID)
        vm.setProvider(AiProviderPreset.CUSTOM)
        vm.setBaseUrl("https://my-proxy.example.com/v1")
        vm.setModel("my-model")
        vm.setName("我的备用")
        vm.save(apiKeyInput = "sk-custom-7777")

        val saved = store.profiles.first().single { !it.isPreset }
        assertEquals("我的备用", saved.name)
        assertNotEquals(AiSettingsViewModel.NEW_PROFILE_ID, saved.id)
        assertTrue(saved.id.startsWith("custom_"))
        assertEquals("sk-custom-7777", store.keys[saved.id])
        assertFalse(vm.uiState.value.isNew) // 保存后成为已落盘档案
    }

    @Test
    fun save_emptyInput_keepsExistingKey() = runTest {
        val id = seededPreset(AiProviderPreset.DEEPSEEK)
        val vm = viewModel(id)
        vm.save(apiKeyInput = "sk-first-key-0001")
        vm.save(apiKeyInput = "") // 不带新 key 再保存 → 原键保留

        assertEquals("sk-first-key-0001", store.keys[id])
    }

    @Test
    fun clearKey_removesKey_butKeepsConfig() = runTest {
        val id = seededPreset(AiProviderPreset.DEEPSEEK)
        val vm = viewModel(id)
        vm.save(apiKeyInput = "sk-abc-9999")
        vm.clearKey()

        assertNull(store.keys[id])
        assertFalse(vm.uiState.value.hasKey)
        assertNull(vm.uiState.value.keyTail)
        // 配置仍在
        assertTrue(vm.uiState.value.profile.isConfigured)
    }

    @Test
    fun save_notConfigured_isNoOp() = runTest {
        val vm = viewModel(AiSettingsViewModel.NEW_PROFILE_ID)
        vm.save(apiKeyInput = "sk-x") // 未填 baseUrl/model → 忽略

        assertTrue(store.keys.isEmpty())
        assertTrue(store.profiles.first().none { !it.isPreset })
        assertFalse(vm.uiState.value.saving)
    }

    @Test
    fun testConnection_success_mapsTestOk() = runTest {
        val id = seededPreset(AiProviderPreset.DEEPSEEK)
        val vm = viewModel(id)
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
        val id = seededPreset(AiProviderPreset.DEEPSEEK)
        val vm = viewModel(id)
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
        val id = seededPreset(AiProviderPreset.DEEPSEEK)
        val vm = viewModel(id)
        vm.save(apiKeyInput = "") // 只保存配置，未录 key

        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.NO_KEY, vm.uiState.value.status)
        assertTrue(remote.requests.isEmpty())
    }

    @Test
    fun testConnection_notConfigured_reportsNotConfigured() = runTest {
        val vm = viewModel(AiSettingsViewModel.NEW_PROFILE_ID)
        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.NOT_CONFIGURED, vm.uiState.value.status)
        assertTrue(remote.requests.isEmpty())
    }

    @Test
    fun init_loadsPersistedProfile() = runTest {
        val id = AiProfile.newCustomId()
        store.seed(
            AiProfile(
                id = id,
                name = "我的通义",
                providerId = AiProviderPreset.QWEN.id,
                baseUrl = "https://x/y",
                model = "m1",
            ),
        )
        store.setApiKey(id, "sk-persisted-1234")

        val vm = viewModel(id)
        val state = vm.uiState.value
        assertEquals(id, state.profile.id)
        assertEquals("我的通义", state.profile.name)
        assertTrue(state.hasKey)
        assertEquals("1234", state.keyTail)
    }

    @Test
    fun setProtocol_updatesState_andPersistsThroughSave() = runTest {
        val id = seededPreset(AiProviderPreset.HUNYUAN)
        val vm = viewModel(id)
        vm.setProtocol(AiApiProtocol.RESPONSES)
        assertEquals(AiApiProtocol.RESPONSES, vm.uiState.value.profile.protocol)

        vm.save(apiKeyInput = "sk-proto-0001")
        assertEquals(AiApiProtocol.RESPONSES, store.profiles.first().first { it.id == id }.protocol)

        // 切回默认协议
        vm.setProtocol(AiApiProtocol.CHAT_COMPLETIONS)
        assertEquals(AiApiProtocol.CHAT_COMPLETIONS, vm.uiState.value.profile.protocol)
    }

    @Test
    fun testConnection_usesSelectedProtocol() = runTest {
        val id = seededPreset(AiProviderPreset.HUNYUAN)
        val vm = viewModel(id)
        vm.setProtocol(AiApiProtocol.RESPONSES)
        vm.save(apiKeyInput = "sk-hy3-key-8888")
        remote.results += AiChatResult.Success("pong", "hy3", null, null)

        vm.testConnection(apiKeyInput = "")
        assertEquals(AiStatus.TEST_OK, vm.uiState.value.status)
        assertEquals(AiApiProtocol.RESPONSES, remote.requests.single().protocol)
    }

    @Test
    fun deleteProfile_preset_refused() = runTest {
        val id = seededPreset(AiProviderPreset.DEEPSEEK)
        val vm = viewModel(id)

        var removed: Boolean? = null
        vm.deleteProfile { removed = it }
        assertEquals(false, removed)
        assertEquals(1, store.profiles.first().count { it.id == id })
    }

    @Test
    fun deleteProfile_custom_succeeds_andClearsKey() = runTest {
        val id = AiProfile.newCustomId()
        store.seed(AiProfile(id = id, name = "临时", providerId = AiProviderPreset.CUSTOM.id, baseUrl = "https://a/b", model = "m"))
        store.setApiKey(id, "sk-temp-0001")
        val vm = viewModel(id)

        var removed: Boolean? = null
        vm.deleteProfile { removed = it }
        assertEquals(true, removed)
        assertTrue(store.profiles.first().none { it.id == id })
        assertNull(store.keys[id])
    }

    @Test
    fun deleteProfile_newUnsaved_justReportsSuccess() = runTest {
        val vm = viewModel(AiSettingsViewModel.NEW_PROFILE_ID)
        var removed: Boolean? = null
        vm.deleteProfile { removed = it }
        assertEquals(true, removed) // 未落盘：直接返回，由 UI popBack
    }

    @Test
    fun init_missingProfile_marksNotConfigured() = runTest {
        // 档案已被删除（列表页删除后返回本页）：置空标记，UI 引导返回。
        val vm = viewModel("custom_deleted")
        assertEquals(AiStatus.NOT_CONFIGURED, vm.uiState.value.status)
    }
}
