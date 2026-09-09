package com.yingjing.pfa.ui

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.core.security.AppLockManager
import com.yingjing.pfa.data.session.DataStoreSessionManager
import com.yingjing.pfa.data.sync.SyncStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RootViewModelStartupTest {
    @Test
    fun real_datastore_session_reaches_logged_out_then_logged_in() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ViewModelStore()
        val session = DataStoreSessionManager(context)
        try {
            session.clear()
            val lock = AppLockManager(session, SyncStateStore(context), backgroundScope)
            val viewModel = RootViewModel_Factory.create(
                Provider { session }, Provider { lock },
            ).get()
            store.put("root", viewModel)
            assertEquals(SessionState.Loading, viewModel.sessionState.value)
            assertEquals(SessionState.LoggedOut, viewModel.sessionState.first { it != SessionState.Loading })
            session.setCurrentUser(7L)
            assertEquals(SessionState.LoggedIn(7), viewModel.sessionState.first { it is SessionState.LoggedIn })
            session.clear()
            assertEquals(SessionState.LoggedOut, viewModel.sessionState.first { it == SessionState.LoggedOut })
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
