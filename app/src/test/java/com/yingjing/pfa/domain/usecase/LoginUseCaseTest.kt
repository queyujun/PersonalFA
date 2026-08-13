package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.auth.LoginResult
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUseCaseTest {

    private val repository = FakeUserRepository()
    private val register = RegisterUserUseCase(repository)
    private val login = LoginUseCase(repository)

    @Test
    fun blankInput_returnsInvalid() = runTest {
        assertTrue(login("", "") is LoginResult.Invalid)
    }

    @Test
    fun correctCredentials_returnsSuccess() = runTest {
        register("alex", "password1")
        assertTrue(login("alex", "password1") is LoginResult.Success)
    }

    @Test
    fun wrongPassword_returnsInvalidCredentials() = runTest {
        register("alex", "password1")
        assertEquals(LoginResult.InvalidCredentials, login("alex", "wrongpass"))
    }

    @Test
    fun unknownUser_returnsInvalidCredentials() = runTest {
        assertEquals(LoginResult.InvalidCredentials, login("ghost", "password1"))
    }
}
