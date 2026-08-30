package com.yingjing.pfa.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.R
import com.yingjing.pfa.data.backup.BackupManager
import com.yingjing.pfa.data.backup.BackupScheduler
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.data.sync.LanguageStore
import com.yingjing.pfa.data.sync.SyncScheduler
import com.yingjing.pfa.core.security.BiometricAuthenticator
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.core.i18n.AppLanguage
import com.yingjing.pfa.PersonalFaApp
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val currentUser: User? = null,
    val users: List<User> = emptyList(),
    val lastSyncMs: Long? = null,
    val autoBackupEnabled: Boolean = false,
    val biometricEnabled: Boolean = true,
    val biometricAvailable: Boolean = false,
    val syncIntervalDays: Int = 1,
    val syncHour: Int = 9,
    val backupIntervalDays: Int = 7,
    val backupHour: Int = 3,
    val currentLanguage: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
    val statusMessage: String? = null,
    val purgeMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val syncScheduler: SyncScheduler,
    private val syncStateStore: SyncStateStore,
    private val backupManager: BackupManager,
    private val backupScheduler: BackupScheduler,
    private val snapshotRepository: SnapshotRepository,
    private val fxRepository: FxRepository,
    private val languageStore: LanguageStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        _uiState.update { it.copy(biometricAvailable = BiometricAuthenticator.isAvailable(context)) }
        viewModelScope.launch {
            syncStateStore.lastSync.collect { ms -> _uiState.update { it.copy(lastSyncMs = ms) } }
        }
        viewModelScope.launch {
            syncStateStore.autoBackupEnabled.collect { on -> _uiState.update { it.copy(autoBackupEnabled = on) } }
        }
        viewModelScope.launch {
            syncStateStore.biometricEnabled.collect { on -> _uiState.update { it.copy(biometricEnabled = on) } }
        }
        viewModelScope.launch {
            syncStateStore.syncIntervalDays.collect { v -> _uiState.update { it.copy(syncIntervalDays = v) } }
        }
        viewModelScope.launch {
            syncStateStore.syncHour.collect { v -> _uiState.update { it.copy(syncHour = v) } }
        }
        viewModelScope.launch {
            syncStateStore.backupIntervalDays.collect { v -> _uiState.update { it.copy(backupIntervalDays = v) } }
        }
        viewModelScope.launch {
            syncStateStore.backupHour.collect { v -> _uiState.update { it.copy(backupHour = v) } }
        }
        viewModelScope.launch {
            languageStore.languageTag.collect { tag ->
                _uiState.update { it.copy(currentLanguage = AppLanguage.fromTag(tag)) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val currentId = sessionManager.currentUserId.first()
            val users = userRepository.listUsers()
            _uiState.update {
                it.copy(
                    currentUser = currentId?.let { id -> users.firstOrNull { u -> u.id == id } },
                    users = users,
                )
            }
        }
    }

    fun logout() = viewModelScope.launch { sessionManager.clear() }

    fun switchUser(userId: Long) = viewModelScope.launch {
        sessionManager.setCurrentUser(userId)
        refresh()
    }

    fun deleteUser(userId: Long) = viewModelScope.launch {
        userRepository.deleteUser(userId)
        if (sessionManager.currentUserId.first() == userId) sessionManager.clear()
        refresh()
    }

    fun refreshQuotesNow() = syncScheduler.refreshNow()

    fun exportTo(uri: Uri, passphrase: String) = viewModelScope.launch {
        val result = runCatching {
            val bytes = backupManager.export(passphrase.toCharArray())
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error(context.getString(R.string.status_write_failed))
        }
        _uiState.update {
            it.copy(
                statusMessage = if (result.isSuccess) context.getString(R.string.status_backup_exported)
                else context.getString(R.string.status_backup_failed, result.exceptionOrNull()?.message ?: ""),
            )
        }
    }

    fun importFrom(uri: Uri, passphrase: String) = viewModelScope.launch {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) {
            _uiState.update { it.copy(statusMessage = context.getString(R.string.status_read_failed)) }
            return@launch
        }
        val ok = backupManager.import(bytes, passphrase.toCharArray())
        _uiState.update {
            it.copy(
                statusMessage = if (ok) context.getString(R.string.status_restore_ok)
                else context.getString(R.string.status_restore_failed),
            )
        }
        if (ok) {
            refresh()
            // 恢复后异步拉取最新汇率：备份里的汇率可能是旧值，联网成功后覆盖为最新；
            // 失败则静默降级，保留从备份写回的汇率（若无则仍默认 1.0）。
            viewModelScope.launch { runCatching { fxRepository.refresh() } }
        }
    }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch {
        syncStateStore.setAutoBackup(enabled)
        backupScheduler.schedule(
            enabled = enabled,
            intervalDays = syncStateStore.backupIntervalDays.first(),
            hour = syncStateStore.backupHour.first(),
            forceReplace = true,
        )
    }

    fun setBiometric(enabled: Boolean) = viewModelScope.launch {
        syncStateStore.setBiometric(enabled)
    }

    fun setSyncSchedule(intervalDays: Int, hour: Int) = viewModelScope.launch {
        syncStateStore.setSyncSchedule(intervalDays, hour)
        syncScheduler.schedule(intervalDays, hour, forceReplace = true)
        _uiState.update { it.copy(statusMessage = context.getString(R.string.status_sync_plan_updated)) }
    }

    fun setBackupSchedule(intervalDays: Int, hour: Int) = viewModelScope.launch {
        syncStateStore.setBackupSchedule(intervalDays, hour)
        if (syncStateStore.autoBackupEnabled.first()) {
            backupScheduler.schedule(true, intervalDays, hour, forceReplace = true)
        }
        _uiState.update { it.copy(statusMessage = context.getString(R.string.status_backup_plan_updated)) }
    }

    fun updateCurrency(currency: Currency) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        userRepository.updateDefaultCurrency(id, currency)
        refresh()
        _uiState.update { it.copy(statusMessage = context.getString(R.string.status_currency_updated)) }
    }

    fun changePassword(old: String, new: String) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        val ok = userRepository.changePassword(id, old, new)
        _uiState.update {
            it.copy(
                statusMessage = if (ok) context.getString(R.string.status_password_changed)
                else context.getString(R.string.status_old_password_wrong),
            )
        }
    }

    fun changeUsername(newName: String) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        val ok = userRepository.changeUsername(id, newName)
        _uiState.update {
            it.copy(
                statusMessage = if (ok) context.getString(R.string.status_username_changed)
                else context.getString(R.string.status_username_taken),
            )
        }
        if (ok) refresh()
    }

    fun updateProfile(nickname: String, gender: String, age: Int?) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        userRepository.updateProfile(id, nickname, gender, age)
        refresh()
        _uiState.update { it.copy(statusMessage = context.getString(R.string.status_profile_saved)) }
    }

    /** 切换应用语言：应用到 AppCompatDelegate + 写入 DataStore + 反馈。 */
    fun setLanguage(lang: AppLanguage) = viewModelScope.launch {
        // 先 applyLanguage（同步设置 AppCompatDelegate 的 per-app locale），再写 DataStore：
        // 这样 languageTag Flow 重发触发资产页 combine 重跑、build() 重新解析文本时，
        // AppStringResolver 读到的已是新 locale，避免「locale 已切换但配置滞后」的竞态。
        PersonalFaApp.applyLanguage(lang.tag)
        languageStore.setLanguage(lang.tag)
        _uiState.update {
            it.copy(currentLanguage = lang, statusMessage = context.getString(R.string.status_language_updated))
        }
    }

    /** 删除当前用户指定日期（不含当天）之前的所有历史快照（净值 + 分类）。 */
    fun purgeSnapshotsBefore(epochDay: Long) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        val result = runCatching { snapshotRepository.deleteBefore(id, epochDay) }
        val dateStr = LocalDate.ofEpochDay(epochDay).toString()
        _uiState.update {
            it.copy(
                purgeMessage = if (result.isSuccess)
                    context.getString(R.string.status_history_deleted, dateStr)
                else context.getString(R.string.status_delete_failed, result.exceptionOrNull()?.message ?: ""),
            )
        }
    }

    fun clearStatus() = _uiState.update { it.copy(statusMessage = null, purgeMessage = null) }
}
