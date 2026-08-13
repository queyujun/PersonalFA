package com.yingjing.pfa.domain.auth

import com.yingjing.pfa.domain.model.User

/** 注册结果。 */
sealed interface RegisterResult {
    data class Success(val user: User) : RegisterResult
    data object UsernameTaken : RegisterResult
    data class Invalid(val reason: String) : RegisterResult
}

/** 登录结果。 */
sealed interface LoginResult {
    data class Success(val user: User) : LoginResult
    data object InvalidCredentials : LoginResult
    data class Invalid(val reason: String) : LoginResult
}
