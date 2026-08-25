package com.yingjing.pfa.data.repository

import com.yingjing.pfa.core.security.PasswordHasher
import com.yingjing.pfa.data.local.UserDao
import com.yingjing.pfa.data.local.UserEntity
import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.domain.repository.UserRepository
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val passwordHasher: PasswordHasher,
) : UserRepository {

    override suspend fun register(
        username: String,
        password: String,
        defaultCurrency: Currency,
    ): RegisterResult {
        if (userDao.findByUsername(username) != null) return RegisterResult.UsernameTaken
        val entity = UserEntity(
            username = username,
            passwordHash = passwordHasher.hash(password),
            defaultCurrency = defaultCurrency.code,
            createdAt = System.currentTimeMillis(),
        )
        val id = userDao.insert(entity)
        return RegisterResult.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun login(username: String, password: String): LoginResult {
        val entity = userDao.findByUsername(username) ?: return LoginResult.InvalidCredentials
        return if (passwordHasher.verify(password, entity.passwordHash)) {
            LoginResult.Success(entity.toDomain())
        } else {
            LoginResult.InvalidCredentials
        }
    }

    override suspend fun listUsers(): List<User> = userDao.getAll().map { it.toDomain() }

    override suspend fun getUser(id: Long): User? = userDao.findById(id)?.toDomain()

    override suspend fun updateDefaultCurrency(userId: Long, currency: Currency) =
        userDao.updateDefaultCurrency(userId, currency.code)

    override suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String): Boolean {
        val entity = userDao.findById(userId) ?: return false
        if (!passwordHasher.verify(oldPassword, entity.passwordHash)) return false
        userDao.updatePassword(userId, passwordHasher.hash(newPassword))
        return true
    }

    override suspend fun changeUsername(userId: Long, newUsername: String): Boolean {
        val name = newUsername.trim()
        if (name.isBlank()) return false
        val existing = userDao.findByUsername(name)
        if (existing != null && existing.id != userId) return false
        userDao.updateUsername(userId, name)
        return true
    }

    override suspend fun updateProfile(userId: Long, nickname: String?, gender: String?, age: Int?) =
        userDao.updateProfile(userId, nickname?.trim()?.ifBlank { null }, gender?.ifBlank { null }, age)

    override suspend fun deleteUser(userId: Long) = userDao.deleteById(userId)
}

private fun UserEntity.toDomain() = User(
    id = id,
    username = username,
    defaultCurrency = Currency.fromCode(defaultCurrency),
    createdAtEpochMs = createdAt,
    nickname = nickname,
    gender = gender,
    age = age,
)
