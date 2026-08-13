package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User

/** 用户仓库：注册/登录/管理。实现负责持久化与密码校验。 */
interface UserRepository {
    suspend fun register(username: String, password: String, defaultCurrency: Currency): RegisterResult
    suspend fun login(username: String, password: String): LoginResult
    suspend fun listUsers(): List<User>
    suspend fun getUser(id: Long): User?
    suspend fun updateDefaultCurrency(userId: Long, currency: Currency)
    suspend fun deleteUser(userId: Long)
}
