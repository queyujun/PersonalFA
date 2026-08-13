package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterUserUseCaseTest {

    private val repository = FakeUserRepository()
    private val useCase = RegisterUserUseCase(repository)

    @Test
    fun shortUsername_returnsInvalid() = runTest {
        assertTrue(useCase("a", "password1") is RegisterResult.Invalid)
    }

    @Test
    fun shortPassword_returnsInvalid() = runTest {
        assertTrue(useCase("alex", "123") is RegisterResult.Invalid)
    }

    @Test
    fun validInput_returnsSuccess() = runTest {
        assertTrue(useCase("alex", "password1") is RegisterResult.Success)
    }

    @Test
    fun duplicateUsername_returnsUsernameTaken() = runTest {
        useCase("alex", "password1")
        assertEquals(RegisterResult.UsernameTaken, useCase("alex", "password2"))
    }
}
