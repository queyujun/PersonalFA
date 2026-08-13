package com.yingjing.pfa.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.domain.usecase.LoginUseCase
import com.yingjing.pfa.domain.usecase.RegisterUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerUser: RegisterUserUseCase,
    private val login: LoginUseCase,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.lastUserId.collect { id ->
                val name = id?.let { userRepository.getUser(it)?.username }
                _uiState.update { it.copy(lastUserId = id, lastUsername = name) }
            }
        }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, error = null) }

    fun onCurrencyChange(currency: Currency) = _uiState.update { it.copy(defaultCurrency = currency) }
    fun setMode(mode: AuthMode) = _uiState.update { it.copy(mode = mode, error = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (state.mode) {
                AuthMode.Login -> handleLogin(state.username, state.password)
                AuthMode.Register -> handleRegister(state)
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun loginWithBiometric() {
        val id = _uiState.value.lastUserId ?: return
        viewModelScope.launch { sessionManager.setCurrentUser(id) }
    }

    private suspend fun handleLogin(username: String, password: String) {
        when (val result = login(username, password)) {
            is LoginResult.Success -> sessionManager.setCurrentUser(result.user.id)
            LoginResult.InvalidCredentials -> setError("用户名或密码错误")
            is LoginResult.Invalid -> setError(result.reason)
        }
    }

    private suspend fun handleRegister(state: AuthUiState) {
        if (state.password != state.confirmPassword) {
            setError("两次输入的密码不一致")
            return
        }
        when (val result = registerUser(state.username, state.password, state.defaultCurrency)) {
            is RegisterResult.Success -> sessionManager.setCurrentUser(result.user.id)
            RegisterResult.UsernameTaken -> setError("用户名已被占用")
            is RegisterResult.Invalid -> setError(result.reason)
        }
    }

    private fun setError(message: String) = _uiState.update { it.copy(error = message) }
}
