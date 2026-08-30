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

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
