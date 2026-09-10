package com.yingjing.pfa.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.data.ai.AiProfile

/**
 * AI 档案列表页（设置 → AI 助手一级页）。
 *
 * 预设服务商 6 行 + 自定义档案区 + 「添加自定义」行：
 * - 行主体点击 → 进编辑页（预设档案/自定义档案/new）；
 * - 行首 RadioButton 点击 → 仅切换「当前生效」，防误触（改某个档案的 key ≠ 要启用它）；
 * - 自定义档案行尾删除按钮 → AlertDialog 确认（删生效档案 → 生效位清空，须重选）。
 */
@Composable
fun AiProfilesScreen(
    onBack: () -> Unit,
    onOpenEdit: (String) -> Unit,
    viewModel: AiProfilesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.ai_settings_title),
        onBack = onBack,
    ) {
        // 预设服务商：每家固定一个档案（未配置也可进入编辑页填写）。
        SettingsGroupCard(icon = Icons.Outlined.Psychology, title = stringResource(R.string.ai_profile_group_preset)) {
            state.presets.forEach { profile ->
                AiProfileRow(
                    profile = profile,
                    isActive = profile.id == state.activeProfileId,
                    onSetActive = viewModel::setActive,
                    onOpen = { onOpenEdit(profile.id) },
                )
            }
        }

        // 自定义档案：可添加多个、可删除。
        SettingsGroupCard(icon = Icons.Outlined.Add, title = stringResource(R.string.ai_profile_group_custom)) {
            if (state.customs.isEmpty()) {
                Text(
                    stringResource(R.string.ai_profile_custom_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else {
                state.customs.forEach { profile ->
                    AiProfileRow(
                        profile = profile,
                        isActive = profile.id == state.activeProfileId,
                        onSetActive = viewModel::setActive,
                        onOpen = { onOpenEdit(profile.id) },
                        onDelete = { viewModel.requestDelete(profile.id) },
                    )
                }
            }
            SettingRow(
                title = stringResource(R.string.ai_profile_add_custom),
                subtitle = stringResource(R.string.ai_profile_add_custom_subtitle),
                onClick = { onOpenEdit(AiSettingsViewModel.NEW_PROFILE_ID) },
            )
        }

        // 生效规则说明：让用户理解「保存/编辑」不会自动切换生效档案。
        Text(
            stringResource(R.string.ai_profile_active_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }

    // 删除确认：随 uiState 单一来源驱动（旋转屏幕后对话框状态保持）。
    val pending = state.pendingDeleteId
    if (pending != null) {
        val target = state.customs.firstOrNull { it.id == pending }
        if (target != null) {
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text(stringResource(R.string.ai_profile_delete_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.ai_profile_delete_confirm_body,
                            target.displayName(),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

/** 档案行：RadioButton（切生效）+ 名称/副标题（点击进编辑）+ 自定义档案的删除按钮。 */
@Composable
private fun AiProfileRow(
    profile: AiProfile,
    isActive: Boolean,
    onSetActive: (String) -> Unit,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isActive,
                onClick = { onSetActive(profile.id) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.displayName(), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (profile.isConfigured) profile.model
                    else stringResource(R.string.settings_ai_subtitle_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isActive) {
                ActiveBadge()
                Spacer(Modifier.width(4.dp))
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 「当前生效」徽章：主题/语言选择器同款样式（primary 底 + 白字）。 */
@Composable
private fun ActiveBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            stringResource(R.string.settings_current_badge),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
