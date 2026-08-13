package com.yingjing.pfa.core.validation

/** 纯函数凭据校验，返回中文错误信息；null 表示通过。 */
object CredentialValidator {
    const val MIN_USERNAME = 2
    const val MAX_USERNAME = 20
    const val MIN_PASSWORD = 6
    const val MAX_PASSWORD = 64

    fun validateUsername(username: String): String? {
        val name = username.trim()
        return when {
            name.isEmpty() -> "请输入用户名"
            name.length < MIN_USERNAME -> "用户名至少 $MIN_USERNAME 个字符"
            name.length > MAX_USERNAME -> "用户名最多 $MAX_USERNAME 个字符"
            !name.all { it.isLetterOrDigit() || it == '_' || it in '一'..'鿿' } ->
                "用户名只能包含中文、字母、数字或下划线"
            else -> null
        }
    }

    fun validatePassword(password: String): String? = when {
        password.isEmpty() -> "请输入密码"
        password.length < MIN_PASSWORD -> "密码至少 $MIN_PASSWORD 位"
        password.length > MAX_PASSWORD -> "密码最多 $MAX_PASSWORD 位"
        else -> null
    }
}
