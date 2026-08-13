package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.core.validation.CredentialValidator
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.repository.UserRepository
import javax.inject.Inject

/** 注册：先做输入校验，再交由仓库落库（含用户名唯一性判断）。 */
class RegisterUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        defaultCurrency: Currency = Currency.CNY,
    ): RegisterResult {
        CredentialValidator.validateUsername(username)?.let { return RegisterResult.Invalid(it) }
        CredentialValidator.validatePassword(password)?.let { return RegisterResult.Invalid(it) }
        return userRepository.register(username.trim(), password, defaultCurrency)
    }
}
