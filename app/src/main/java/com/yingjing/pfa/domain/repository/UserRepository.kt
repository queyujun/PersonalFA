package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User
import kotlinx.coroutines.flow.Flow

/** 用户仓库：注册/登录/管理。实现负责持久化与密码校验。 */
interface UserRepository {
    suspend fun register(username: String, password: String, defaultCurrency: Currency): RegisterResult
    suspend fun login(username: String, password: String): LoginResult
    suspend fun listUsers(): List<User>
    suspend fun getUser(id: Long): User?

    /** 响应式观察单个用户：整表替换（恢复备份）后自动重发最新值。 */
    fun observeUser(id: Long): Flow<User?>

    suspend fun updateDefaultCurrency(userId: Long, currency: Currency)

    /** 修改密码：旧密码校验通过才改，返回是否成功。 */
    suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String): Boolean

    /** 修改用户名：与他人重名返回 false。 */
    suspend fun changeUsername(userId: Long, newUsername: String): Boolean

    /** 更新资料（昵称 / 性别 / 年龄）。 */
    suspend fun updateProfile(userId: Long, nickname: String?, gender: String?, age: Int?)

    suspend fun deleteUser(userId: Long)
}
