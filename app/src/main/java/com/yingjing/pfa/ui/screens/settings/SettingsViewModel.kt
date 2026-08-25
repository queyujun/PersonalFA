package com.yingjing.pfa.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.backup.BackupManager
import com.yingjing.pfa.data.backup.BackupScheduler
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.data.sync.SyncScheduler
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val currentUser: User? = null,
    val users: List<User> = emptyList(),
    val lastSyncMs: Long? = null,
    val autoBackupEnabled: Boolean = false,
    val statusMessage: String? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            syncStateStore.lastSync.collect { ms -> _uiState.update { it.copy(lastSyncMs = ms) } }
        }
        viewModelScope.launch {
            syncStateStore.autoBackupEnabled.collect { on -> _uiState.update { it.copy(autoBackupEnabled = on) } }
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
                ?: error("无法写入文件")
        }
        _uiState.update {
            it.copy(statusMessage = if (result.isSuccess) "✓ 备份已导出" else "备份失败：${result.exceptionOrNull()?.message}")
        }
    }

    fun importFrom(uri: Uri, passphrase: String) = viewModelScope.launch {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) {
            _uiState.update { it.copy(statusMessage = "读取文件失败") }
            return@launch
        }
        val ok = backupManager.import(bytes, passphrase.toCharArray())
        _uiState.update {
            it.copy(statusMessage = if (ok) "✓ 恢复成功" else "恢复失败：口令错误或文件无效")
        }
        if (ok) refresh()
    }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch {
        syncStateStore.setAutoBackup(enabled)
        backupScheduler.setEnabled(enabled)
    }

    fun updateCurrency(currency: Currency) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        userRepository.updateDefaultCurrency(id, currency)
        refresh()
        _uiState.update { it.copy(statusMessage = "✓ 默认货币已更新") }
    }

    fun changePassword(old: String, new: String) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        val ok = userRepository.changePassword(id, old, new)
        _uiState.update { it.copy(statusMessage = if (ok) "✓ 密码已修改" else "旧密码不正确") }
    }

    fun changeUsername(newName: String) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        val ok = userRepository.changeUsername(id, newName)
        _uiState.update { it.copy(statusMessage = if (ok) "✓ 用户名已修改" else "用户名已被占用或无效") }
        if (ok) refresh()
    }

    fun updateProfile(nickname: String, gender: String, age: Int?) = viewModelScope.launch {
        val id = sessionManager.currentUserId.first() ?: return@launch
        userRepository.updateProfile(id, nickname, gender, age)
        refresh()
        _uiState.update { it.copy(statusMessage = "✓ 资料已保存") }
    }

    fun clearStatus() = _uiState.update { it.copy(statusMessage = null) }
}
