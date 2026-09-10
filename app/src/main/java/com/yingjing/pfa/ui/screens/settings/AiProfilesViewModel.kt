package com.yingjing.pfa.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AI 档案列表页状态：档案分区 + 生效 id。 */
data class AiProfilesUiState(
    val presets: List<AiProfile> = emptyList(),
    val customs: List<AiProfile> = emptyList(),
    val activeProfileId: String? = null,
    /** 待删除确认的自定义档案 id；null 表示无待确认项。 */
    val pendingDeleteId: String? = null,
)

/**
 * AI 档案列表页 ViewModel（设置 → AI 助手的一级页）。
 *
 * 预设 6 档案固定展示；自定义档案区在其后；「添加自定义」进入新建编辑页。
 * RadioButton 切换生效档案；行主体点击进编辑页。
 */
@HiltViewModel
class AiProfilesViewModel @Inject constructor(
    private val aiSettingsStore: AiSettingsStore,
) : ViewModel() {

    private val _pendingDelete = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AiProfilesUiState> =
        combine(
            aiSettingsStore.profiles,
            aiSettingsStore.activeProfileId,
            _pendingDelete,
        ) { list, activeId, pendingDelete ->
            AiProfilesUiState(
                presets = list.filter { it.isPreset },
                customs = list.filter { !it.isPreset },
                activeProfileId = activeId,
                pendingDeleteId = pendingDelete,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProfilesUiState())

    /** 单选生效档案（RadioButton 点击；档案须存在于列表）。 */
    fun setActive(profileId: String) {
        viewModelScope.launch { aiSettingsStore.setActiveProfile(profileId) }
    }

    /** 请求删除自定义档案（UI 弹确认框后调 [confirmDelete]）。 */
    fun requestDelete(profileId: String) {
        _pendingDelete.value = profileId
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    /** 确认删除（仅自定义档案可删；删生效档案 → 生效位清空，用户须重选）。 */
    fun confirmDelete() {
        val id = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { aiSettingsStore.deleteProfile(id) }
    }
}
