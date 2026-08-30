package com.yingjing.pfa.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.Currency

/**
 * 个人资料二级界面：只读展示 @用户名 + 编辑昵称/性别/年龄/默认货币。
 * 复用一级 [SettingsScreen] 的共享 [SettingsViewModel] 实例，保存后一级摘要自动更新。
 */
@Composable
fun ProfileEditScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.clearStatus() }

    SettingsDetailScaffold(title = stringResource(R.string.settings_group_profile), onBack = onBack) {
        // 只读用户名卡（用户名在「账号与安全」修改，这里仅展示）。
        SettingsGroupCard(icon = Icons.Outlined.Person, title = stringResource(R.string.profile_account)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "@${state.currentUser?.username ?: "-"}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onBack,
                    enabled = false,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) { Text(stringResource(R.string.profile_readonly)) }
            }
        }

        // 可编辑资料卡（昵称/性别/年龄/货币 + 保存）。
        SettingsGroupCard(icon = Icons.Outlined.Person, title = stringResource(R.string.settings_row_profile)) {
            ProfileFields(
                nickname0 = state.currentUser?.nickname ?: "",
                gender0 = state.currentUser?.gender ?: "",
                age0 = state.currentUser?.age,
                currency = state.currentUser?.defaultCurrency ?: Currency.CNY,
                onCurrency = viewModel::updateCurrency,
                onSave = { n, g, a -> viewModel.updateProfile(n, g, a) },
            )
        }

        state.statusMessage?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
