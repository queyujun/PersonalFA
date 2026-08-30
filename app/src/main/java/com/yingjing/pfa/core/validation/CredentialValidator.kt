package com.yingjing.pfa.core.validation

import com.yingjing.pfa.R

/** 纯函数凭据校验，返回 [ValidationFailure]；null 表示通过。文案由渲染端经 StringResolver 解析。 */
object CredentialValidator {
    const val MIN_USERNAME = 2
    const val MAX_USERNAME = 20
    const val MIN_PASSWORD = 6
    const val MAX_PASSWORD = 64

    fun validateUsername(username: String): ValidationFailure? {
        val name = username.trim()
        return when {
            name.isEmpty() -> ValidationFailure(R.string.err_username_empty)
            name.length < MIN_USERNAME -> ValidationFailure(R.string.err_username_min, listOf(MIN_USERNAME))
            name.length > MAX_USERNAME -> ValidationFailure(R.string.err_username_max, listOf(MAX_USERNAME))
            !name.all { it.isLetterOrDigit() || it == '_' || it in '一'..'鿿' } ->
                ValidationFailure(R.string.err_username_chars)
            else -> null
        }
    }

    fun validatePassword(password: String): ValidationFailure? = when {
        password.isEmpty() -> ValidationFailure(R.string.err_password_empty)
        password.length < MIN_PASSWORD -> ValidationFailure(R.string.err_password_min, listOf(MIN_PASSWORD))
        password.length > MAX_PASSWORD -> ValidationFailure(R.string.err_password_max, listOf(MAX_PASSWORD))
        else -> null
    }
}
