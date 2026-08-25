package com.yingjing.pfa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.core.security.PasswordHasher
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.Currency
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: UserRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = UserRepositoryImpl(db.userDao(), PasswordHasher())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun register_thenLogin_succeeds() = runTest {
        assertTrue(repository.register("alex", "password1", Currency.CNY) is RegisterResult.Success)
        assertTrue(repository.login("alex", "password1") is LoginResult.Success)
    }

    @Test
    fun register_duplicate_returnsUsernameTaken() = runTest {
        repository.register("alex", "password1", Currency.CNY)
        assertEquals(RegisterResult.UsernameTaken, repository.register("alex", "other123", Currency.CNY))
    }

    @Test
    fun login_wrongPassword_returnsInvalidCredentials() = runTest {
        repository.register("alex", "password1", Currency.CNY)
        assertEquals(LoginResult.InvalidCredentials, repository.login("alex", "wrongpass"))
    }

    @Test
    fun password_isStoredHashed_notPlaintext() = runTest {
        repository.register("alex", "password1", Currency.CNY)
        val entity = db.userDao().findByUsername("alex")!!
        assertNotEquals("password1", entity.passwordHash)
        assertTrue(entity.passwordHash.startsWith("pbkdf2:"))
    }

    @Test
    fun updateDefaultCurrency_persists() = runTest {
        val user = (repository.register("alex", "password1", Currency.CNY) as RegisterResult.Success).user
        repository.updateDefaultCurrency(user.id, Currency.USD)
        assertEquals(Currency.USD, repository.getUser(user.id)!!.defaultCurrency)
    }

    @Test
    fun deleteUser_removesUser() = runTest {
        val user = (repository.register("alex", "password1", Currency.CNY) as RegisterResult.Success).user
        repository.deleteUser(user.id)
        assertNull(repository.getUser(user.id))
    }

    @Test
    fun changePassword_withCorrectOld_succeeds_andRebindsLogin() = runTest {
        val user = (repository.register("alex", "password1", Currency.CNY) as RegisterResult.Success).user
        assertTrue(repository.changePassword(user.id, "password1", "newpass2"))
        assertTrue(repository.login("alex", "newpass2") is LoginResult.Success)
        assertEquals(LoginResult.InvalidCredentials, repository.login("alex", "password1"))
    }

    @Test
    fun changePassword_withWrongOld_failsAndKeepsOld() = runTest {
        val user = (repository.register("alex", "password1", Currency.CNY) as RegisterResult.Success).user
        assertFalse(repository.changePassword(user.id, "wrongold", "newpass2"))
        assertTrue(repository.login("alex", "password1") is LoginResult.Success)
    }

    @Test
    fun changeUsername_toFreeName_succeeds() = runTest {
        val user = (repository.register("alex", "password1", Currency.CNY) as RegisterResult.Success).user
        assertTrue(repository.changeUsername(user.id, "alex2"))
        assertEquals("alex2", repository.getUser(user.id)!!.username)
    }

    @Test
    fun changeUsername_toTakenName_failsAndKeepsOld() = runTest {
        val a = (repository.register("alex", "password1", Currency.CNY) as RegisterResult.Success).user
        repository.register("bob", "password1", Currency.CNY)
        assertFalse(repository.changeUsername(a.id, "bob"))
        assertEquals("alex", repository.getUser(a.id)!!.username)
    }

    @Test
    fun updateProfile_persistsNicknameGenderAge() = runTest {
        val user = (repository.register("alex", "password1", Currency.CNY) as RegisterResult.Success).user
        repository.updateProfile(user.id, "阿历", "男", 30)
        val u = repository.getUser(user.id)!!
        assertEquals("阿历", u.nickname)
        assertEquals("男", u.gender)
        assertEquals(30, u.age)
    }
}
