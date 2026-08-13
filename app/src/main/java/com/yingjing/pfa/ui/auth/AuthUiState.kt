package com.yingjing.pfa.ui.auth

import com.yingjing.pfa.domain.model.Currency

enum class AuthMode { Login, Register }

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val defaultCurrency: Currency = Currency.CNY,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val lastUserId: Long? = null,
    val lastUsername: String? = null,
)
