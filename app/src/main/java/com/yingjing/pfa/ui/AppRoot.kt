package com.yingjing.pfa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.ui.auth.AuthScreen
import com.yingjing.pfa.ui.navigation.PersonalFaRoot

/** 应用根：按会话状态在「登录」与「主界面」之间切换。 */
@Composable
fun AppRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val state by rootViewModel.sessionState.collectAsState()
    when (state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize())
        SessionState.LoggedOut -> AuthScreen()
        is SessionState.LoggedIn -> PersonalFaRoot()
    }
}
