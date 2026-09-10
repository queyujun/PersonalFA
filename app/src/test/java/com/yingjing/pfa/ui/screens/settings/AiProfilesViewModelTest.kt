package com.yingjing.pfa.ui.screens.settings

import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiProviderPreset
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AiProfilesViewModel（档案列表页）逻辑测试：presets/customs 分区与生效投影、
 * setActive 切换、pendingDelete 确认流（置位→取消/确认）、删生效档案 → active 置 null。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiProfilesViewModelTest {

    private val store = FakeAiSettingsStore()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = AiProfilesViewModel(store)

    /** 落盘一个预设档案（默认已配置状态）。 */
    private fun seedPreset(
        provider: AiProviderPreset,
        baseUrl: String = provider.defaultBaseUrl,
        model: String = provider.defaultModel,
    ): String {
        val id = AiProfile.presetIdOf(provider)
        store.seed(
            AiProfile(id = id, providerId = provider.id, baseUrl = baseUrl, model = model),
        )
        return id
    }

    /** 落盘一个自定义档案并返回其 id。 */
    private fun seedCustom(name: String = "自定义"): String {
        val id = AiProfile.newCustomId()
        store.seed(
            AiProfile(
                id = id, name = name, providerId = AiProviderPreset.CUSTOM.id,
                baseUrl = "https://proxy.example.com/v1", model = "m1",
            ),
        )
        return id
    }

    @Test
    fun uiState_partitionsPresetsAndCustoms_andProjectsActive() = runTest {
        val deepseek = seedPreset(AiProviderPreset.DEEPSEEK)
        val kimi = seedPreset(AiProviderPreset.KIMI, baseUrl = "", model = "") // 未配置预设
        val custom = seedCustom("我的代理")
        store.setActiveProfile(custom)

        val state = vm().uiState.first()
        assertEquals(listOf(deepseek, kimi).sorted(), state.presets.map { it.id }.sorted())
        assertTrue(state.presets.all { it.isPreset })
        assertEquals(listOf(custom), state.customs.map { it.id })
        assertEquals(custom, state.activeProfileId)
        assertNull(state.pendingDeleteId)
    }

    @Test
    fun setActive_switchesActiveProfile() = runTest {
        val deepseek = seedPreset(AiProviderPreset.DEEPSEEK)
        seedCustom()
        val vm = vm()

        vm.setActive(deepseek)
        assertEquals(deepseek, store.activeProfileId.first())
        assertEquals(deepseek, vm.uiState.first().activeProfileId)
    }

    @Test
    fun setActive_unknownId_isIgnored() = runTest {
        val deepseek = seedPreset(AiProviderPreset.DEEPSEEK)
        store.setActiveProfile(deepseek)
        val vm = vm()

        vm.setActive("custom_not_exists")
        assertEquals(deepseek, store.activeProfileId.first()) // 不被未知 id 覆盖
    }

    @Test
    fun requestDelete_setsPending_thenCancelClears() = runTest {
        val custom = seedCustom()
        val vm = vm()

        vm.requestDelete(custom)
        assertEquals(custom, vm.uiState.first().pendingDeleteId)
        assertEquals(1, store.profiles.first().count { it.id == custom }) // 尚未删除

        vm.cancelDelete()
        assertNull(vm.uiState.first().pendingDeleteId)
        assertEquals(1, store.profiles.first().count { it.id == custom })
    }

    @Test
    fun confirmDelete_removesCustomProfile_andClearsPending() = runTest {
        val custom = seedCustom("临时")
        store.setApiKey(custom, "sk-tmp-0001")
        val vm = vm()

        vm.requestDelete(custom)
        vm.confirmDelete()

        assertTrue(store.profiles.first().none { it.id == custom })
        assertNull(store.keys[custom]) // key 一并清除
        assertNull(vm.uiState.first().pendingDeleteId)
    }

    @Test
    fun confirmDelete_deletingActiveCustom_clearsActive() = runTest {
        val deepseek = seedPreset(AiProviderPreset.DEEPSEEK)
        val custom = seedCustom()
        store.setActiveProfile(custom)
        val vm = vm()

        vm.requestDelete(custom)
        vm.confirmDelete()

        assertNull(store.activeProfileId.first()) // 生效位清空，须重选
        // 预设档案仍在
        assertTrue(store.profiles.first().any { it.id == deepseek })
    }

    @Test
    fun confirmDelete_withoutPending_isNoOp() = runTest {
        val custom = seedCustom()
        val vm = vm()

        vm.confirmDelete() // 无待确认项 → 不抛错、不误删

        assertEquals(1, store.profiles.first().count { it.id == custom })
    }

    @Test
    fun confirmDelete_preset_refusedByStore() = runTest {
        val deepseek = seedPreset(AiProviderPreset.DEEPSEEK)
        store.setActiveProfile(deepseek)
        val vm = vm()

        vm.requestDelete(deepseek)
        vm.confirmDelete()

        assertTrue(store.profiles.first().any { it.id == deepseek }) // 预设不可删
        assertEquals(deepseek, store.activeProfileId.first())        // 生效位不受影响
    }
}
