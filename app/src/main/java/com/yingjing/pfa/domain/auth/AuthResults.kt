package com.yingjing.pfa.domain.auth

import com.yingjing.pfa.core.validation.ValidationFailure
import com.yingjing.pfa.domain.model.User

/** 注册结果。[Invalid] 携带未解析的 [ValidationFailure]，由渲染端经 StringResolver 解析文案。 */
sealed interface RegisterResult {
    data class Success(val user: User) : RegisterResult
    data object UsernameTaken : RegisterResult
    data class Invalid(val failure: ValidationFailure) : RegisterResult
}

/** 登录结果。[Invalid] 携带未解析的 [ValidationFailure]。 */
sealed interface LoginResult {
    data class Success(val user: User) : LoginResult
    data object InvalidCredentials : LoginResult
    data class Invalid(val failure: ValidationFailure) : LoginResult
}
