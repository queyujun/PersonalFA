package com.yingjing.pfa.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.R
import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.core.security.AppLockManager
import com.yingjing.pfa.core.security.BiometricAuthenticator
import com.yingjing.pfa.core.validation.ValidationFailure
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val biometricEnabled: Boolean = true,
    val biometricAvailable: Boolean = false,
)

/**
 * 应用锁遮罩 ViewModel：为 [LockScreen] 提供当前用户名、指纹可用性，以及密码 / 指纹解锁逻辑。
 *
 * - 密码解锁复用 [UserRepository.login]（内部 PBKDF2 校验），无需改仓库接口。
 * - 指纹解锁调用 [AppLockManager.unlockByBiometric]——设备指纹=已登录身份凭证，
 *   与 [AuthViewModel.loginWithBiometric] 同语义，不重新校验密码。
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockManager: AppLockManager,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val syncStateStore: SyncStateStore,
    private val stringResolver: StringResolver,
) : ViewModel() {

    /** 当前登录用户名；未登录时为 null。 */
    val username: StateFlow<String?> = sessionManager.currentUserId
        .map { id -> id?.let { userRepository.getUser(it)?.username } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(
        AppLockUiState(biometricAvailable = BiometricAuthenticator.isAvailable(context)),
    )
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            syncStateStore.biometricEnabled.collect { on ->
                _uiState.update { it.copy(biometricEnabled = on) }
            }
        }
    }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun unlockWithPassword() {
        val state = _uiState.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val id = sessionManager.currentUserId.first()
            val username = id?.let { userRepository.getUser(it)?.username }
            if (id == null || username == null) {
                _uiState.update { it.copy(isSubmitting = false, error = stringResolver.get(R.string.lock_wrong_password)) }
                return@launch
            }
            when (val result = userRepository.login(username, state.password)) {
                is LoginResult.Success -> {
                    appLockManager.unlock()
                    _uiState.update { it.copy(isSubmitting = false) }
                }
                LoginResult.InvalidCredentials ->
                    _uiState.update { it.copy(isSubmitting = false, error = stringResolver.get(R.string.lock_wrong_password)) }
                is LoginResult.Invalid ->
                    _uiState.update { it.copy(isSubmitting = false, error = result.failure.resolve()) }
            }
        }
    }

    fun unlockByBiometric() = appLockManager.unlockByBiometric()

    /** 把 [ValidationFailure] 经 [stringResolver] 解析为当前 locale 文案。 */
    private fun ValidationFailure.resolve(): String =
        if (args.isEmpty()) stringResolver.get(resId)
        else stringResolver.get(resId, *args.toTypedArray())
}
