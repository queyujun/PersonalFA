package com.yingjing.pfa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.ui.auth.AuthScreen
import com.yingjing.pfa.ui.auth.LockScreen
import com.yingjing.pfa.ui.navigation.PersonalFaRoot

/**
 * 应用根：按会话状态在「登录」与「主界面」之间切换。
 *
 * 已登录时在主界面之上挂载 [LockScreen] 遮罩——[isLocked] 时覆盖当前页，
 * 解锁后回到之前正在看的页面（导航状态不丢失），类似银行 / 支付宝。
 */
@Composable
fun AppRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val state by rootViewModel.sessionState.collectAsState()
    when (state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize())
        SessionState.LoggedOut -> AuthScreen()
        is SessionState.LoggedIn -> {
            val isLocked by rootViewModel.isLocked.collectAsState()
            Box(Modifier.fillMaxSize()) {
                PersonalFaRoot()
                if (isLocked) LockScreen()
            }
        }
    }
}
