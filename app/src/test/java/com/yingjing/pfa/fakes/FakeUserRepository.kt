package com.yingjing.pfa.fakes

import com.yingjing.pfa.core.security.PasswordHasher
import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.domain.repository.UserRepository

/** 内存版 UserRepository，用于 UseCase / ViewModel 单元测试。 */
class FakeUserRepository(
    private val hasher: PasswordHasher = PasswordHasher(),
) : UserRepository {
    private val users = mutableListOf<User>()
    private val hashes = mutableMapOf<Long, String>()
    private var nextId = 1L

    override suspend fun register(
        username: String,
        password: String,
        defaultCurrency: Currency,
    ): RegisterResult {
        if (users.any { it.username == username }) return RegisterResult.UsernameTaken
        val user = User(nextId++, username, defaultCurrency, 0L)
        users += user
        hashes[user.id] = hasher.hash(password, iterations = 1000)
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

    override suspend fun updateDefaultCurrency(userId: Long, currency: Currency) {
        val index = users.indexOfFirst { it.id == userId }
        if (index >= 0) users[index] = users[index].copy(defaultCurrency = currency)
    }

    override suspend fun deleteUser(userId: Long) {
        users.removeAll { it.id == userId }
        hashes.remove(userId)
    }
}
