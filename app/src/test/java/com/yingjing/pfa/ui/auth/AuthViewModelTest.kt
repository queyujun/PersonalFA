package com.yingjing.pfa.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.usecase.LoginUseCase
import com.yingjing.pfa.domain.usecase.RegisterUserUseCase
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeStringResolver
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeUserRepository
    private lateinit var session: FakeSessionManager
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeUserRepository()
        session = FakeSessionManager()
        val syncStateStore = SyncStateStore(ApplicationProvider.getApplicationContext<Context>())
        viewModel = AuthViewModel(
            registerUser = RegisterUserUseCase(repository),
            login = LoginUseCase(repository),
            sessionManager = session,
            userRepository = repository,
            syncStateStore = syncStateStore,
            stringResolver = FakeStringResolver(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun register_success_setsSession() = runTest(dispatcher.scheduler) {
        viewModel.setMode(AuthMode.Register)
        viewModel.onUsernameChange("alex")
        viewModel.onPasswordChange("password1")
        viewModel.onConfirmPasswordChange("password1")
        viewModel.submit()
        advanceUntilIdle()
        assertNotNull(session.current)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun register_passwordMismatch_setsError() = runTest(dispatcher.scheduler) {
        viewModel.setMode(AuthMode.Register)
        viewModel.onUsernameChange("alex")
        viewModel.onPasswordChange("password1")
        viewModel.onConfirmPasswordChange("password2")
        viewModel.submit()
        advanceUntilIdle()
        assertNull(session.current)
        // FakeStringResolver 把 err_password_mismatch 渲染为非空标识；此处只验错误键被触发，不绑 locale。
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun login_wrongPassword_setsError() = runTest(dispatcher.scheduler) {
        repository.register("alex", "password1", Currency.CNY)
        viewModel.onUsernameChange("alex")
        viewModel.onPasswordChange("wrongpass")
        viewModel.submit()
        advanceUntilIdle()
        assertNull(session.current)
        // FakeStringResolver 把 err_login_invalid 渲染为非空标识；此处只验错误键被触发，不绑 locale。
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun login_success_setsSession() = runTest(dispatcher.scheduler) {
        repository.register("alex", "password1", Currency.CNY)
        viewModel.onUsernameChange("alex")
        viewModel.onPasswordChange("password1")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(1L, session.current)
    }
}
