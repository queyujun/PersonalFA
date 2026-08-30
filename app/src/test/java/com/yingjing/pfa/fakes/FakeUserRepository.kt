package com.yingjing.pfa.fakes

import com.yingjing.pfa.core.security.PasswordHasher
import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 内存版 UserRepository，用于 UseCase / ViewModel 单元测试。 */
class FakeUserRepository(
    private val hasher: PasswordHasher = PasswordHasher(),
) : UserRepository {
    private val users = mutableListOf<User>()
    private val hashes = mutableMapOf<Long, String>()
    private var nextId = 1L

    // 响应式状态：任一变更后 push 最新列表，observeUser 据此重发。
    private val usersState = MutableStateFlow(users.toList())

    override suspend fun register(
        username: String,
        password: String,
        defaultCurrency: Currency,
    ): RegisterResult {
        if (users.any { it.username == username }) return RegisterResult.UsernameTaken
        val user = User(nextId++, username, defaultCurrency, 0L)
        users += user
        hashes[user.id] = hasher.hash(password, iterations = 1000)
        publish()
        return RegisterResult.Success(user)
    }

    override suspend fun login(username: String, password: String): LoginResult {
        val user = users.firstOrNull { it.username == username } ?: return LoginResult.InvalidCredentials
        return if (hasher.verify(password, hashes.getValue(user.id))) {
            LoginResult.Success(user)
        } else {
            LoginResult.InvalidCredentials
        }
    }

    override suspend fun listUsers(): List<User> = users.toList()

    override suspend fun getUser(id: Long): User? = users.firstOrNull { it.id == id }

    override fun observeUser(id: Long): Flow<User?> =
        usersState.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun updateDefaultCurrency(userId: Long, currency: Currency) {
        val index = users.indexOfFirst { it.id == userId }
        if (index >= 0) {
            users[index] = users[index].copy(defaultCurrency = currency)
            publish()
        }
    }

    override suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String): Boolean {
        val stored = hashes[userId] ?: return false
        if (!hasher.verify(oldPassword, stored)) return false
        hashes[userId] = hasher.hash(newPassword, iterations = 1000)
        return true
    }

    override suspend fun changeUsername(userId: Long, newUsername: String): Boolean {
        val name = newUsername.trim()
        if (name.isBlank()) return false
        if (users.any { it.username == name && it.id != userId }) return false
        val index = users.indexOfFirst { it.id == userId }
        if (index >= 0) {
            users[index] = users[index].copy(username = name)
            publish()
        }
        return true
    }

    override suspend fun updateProfile(userId: Long, nickname: String?, gender: String?, age: Int?) {
        val index = users.indexOfFirst { it.id == userId }
        if (index >= 0) {
            users[index] = users[index].copy(nickname = nickname, gender = gender, age = age)
            publish()
        }
    }

    override suspend fun deleteUser(userId: Long) {
        users.removeAll { it.id == userId }
        hashes.remove(userId)
        publish()
    }

    private fun publish() {
        usersState.value = users.toList()
    }
}
