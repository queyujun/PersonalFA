package com.yingjing.pfa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.yingjing.pfa.core.security.AppLockManager
import com.yingjing.pfa.data.sync.ThemeStore
import com.yingjing.pfa.ui.AppRoot
import com.yingjing.pfa.ui.theme.PersonalFaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// 继承 AppCompatActivity 以支持 per-app locale 切换（AppCompatDelegate）；
// AppCompatActivity 继承 FragmentActivity，BiometricPrompt 仍可用。
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var themeStore: ThemeStore

    @Inject
    lateinit var appLockManager: AppLockManager

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 观察进程级前后台切换：应用退到后台（ON_STOP）时由 AppLockManager 置锁，
        // 切回前台需重新认证（密码 / 指纹）才能继续浏览。
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            val themeId by themeStore.themeId.collectAsState(initial = null)
            // 深浅模式默认跟随系统（PersonalFaTheme 内部 isSystemInDarkTheme）；
            // 主题 id 由设置页持久化选择，null 时回退默认莫兰迪。
            PersonalFaTheme(themeId = themeId) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
