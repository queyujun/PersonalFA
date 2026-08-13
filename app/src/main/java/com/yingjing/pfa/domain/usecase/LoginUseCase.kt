package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.repository.UserRepository
import javax.inject.Inject

/** 登录：基础非空校验后交由仓库校验密码。 */
class LoginUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(username: String, password: String): LoginResult {
        if (username.isBlank() || password.isEmpty()) {
            return LoginResult.Invalid("请输入用户名和密码")
        }
        return userRepository.login(username.trim(), password)
    }
}
