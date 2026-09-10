package com.yingjing.pfa.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.BuildConfig
import com.yingjing.pfa.R
import com.yingjing.pfa.core.i18n.AppLanguage
import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.ui.theme.AppTheme
import com.yingjing.pfa.ui.theme.LocalBrandColors

/**
 * 设置页（一级）：9 个语义分组卡片，仅显示设置好的结果摘要 + › 进入二级界面。
 * 「个人资料」「数据与同步」「数据备份」「AI 助手」的可编辑项下沉到各自二级界面；
 * 「账号与安全」「账号管理」「语言」保持原有点击/对话框模式（用户未要求改动）。
 */
@Composable
fun SettingsScreen(
    onOpenProfile: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAi: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.clearStatus() }

    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // 设置页背景：取当前主题品牌色提供的页面底色（已按系统深浅解析），
    // 默认 background 与卡片 surface 接近时卡片不凸显，故用更深的暖灰，且随主题变化。
    val pageBg = LocalBrandColors.current.pageBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GradientUserHeader(user = state.currentUser, onLogout = viewModel::logout)

        // 1. 个人资料（摘要 → 二级）
        SettingsGroupCard(icon = Icons.Outlined.Person, title = stringResource(R.string.settings_group_profile)) {
            SettingRow(
                title = stringResource(R.string.settings_row_profile),
                subtitle = profileSummary(state.currentUser),
                onClick = onOpenProfile,
            )
        }

        // 2. 账号与安全（保持原有点击/对话框模式）
        SettingsGroupCard(icon = Icons.Outlined.Lock, title = stringResource(R.string.settings_group_security)) {
            SettingRow(title = stringResource(R.string.settings_change_username), onClick = { showUsernameDialog = true })
            SettingRow(title = stringResource(R.string.settings_change_password), onClick = { showPasswordDialog = true })
            if (state.biometricAvailable) {
                SettingRow(
                    title = stringResource(R.string.settings_biometric),
                    subtitle = stringResource(R.string.settings_biometric_subtitle),
                    trailing = {
                        Switch(
                            checked = state.biometricEnabled,
                            onCheckedChange = { viewModel.setBiometric(it) },
                        )
                    },
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_app_lock),
                subtitle = stringResource(R.string.settings_app_lock_subtitle),
                trailing = {
                    Switch(
                        checked = state.appLockEnabled,
                        onCheckedChange = { viewModel.setAppLock(it) },
                    )
                },
            )
        }

        // 3. 账号管理（保持不变）
        SettingsGroupCard(icon = Icons.Outlined.Group, title = stringResource(R.string.settings_group_accounts)) {
            state.users.forEach { user ->
                UserItemRow(
                    user = user,
                    isCurrent = user.id == state.currentUser?.id,
                    onSwitch = viewModel::switchUser,
                    onDelete = viewModel::deleteUser,
                )
            }
        }

        // 4. 数据与同步（摘要 → 二级）
        SettingsGroupCard(icon = Icons.Outlined.Sync, title = stringResource(R.string.settings_group_sync)) {
            SettingRow(
                title = stringResource(R.string.settings_sync_detail),
                subtitle = "${scheduleText(state.syncIntervalDays, state.syncHour)} · " +
                    stringResource(
                        R.string.settings_last_update,
                        state.lastSyncMs?.let { formatTime(it) } ?: stringResource(R.string.settings_not_updated),
                    ),
                onClick = onOpenSync,
            )
        }

        // 5. 数据备份（摘要 → 二级）
        SettingsGroupCard(icon = Icons.Outlined.CloudUpload, title = stringResource(R.string.settings_group_backup)) {
            SettingRow(
                title = stringResource(R.string.settings_backup_detail),
                subtitle = stringResource(R.string.settings_auto_backup) + " · " +
                    (if (state.autoBackupEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off)) +
                    " · " + scheduleText(state.backupIntervalDays, state.backupHour),
                onClick = onOpenBackup,
            )
        }

        // 6. 语言（对话框切换）
        SettingsGroupCard(icon = Icons.Outlined.Language, title = stringResource(R.string.settings_group_language)) {
            SettingRow(
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(state.currentLanguage.labelRes),
                onClick = { showLanguageDialog = true },
            )
        }

        // 7. 外观（主题选择 + 深浅跟随系统）
        SettingsGroupCard(icon = Icons.Outlined.Palette, title = stringResource(R.string.settings_group_appearance)) {
            SettingRow(
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(state.currentTheme.labelRes),
                onClick = { showThemeDialog = true },
            )
            SettingRow(
                title = stringResource(R.string.settings_dark_mode),
                subtitle = stringResource(R.string.settings_dark_mode_subtitle),
            )
        }

        // 8. AI 助手（摘要 → 档案列表页；副标题展示「生效档案 · 已配置模型 / 未配置」）
        SettingsGroupCard(icon = Icons.Outlined.Psychology, title = stringResource(R.string.settings_group_ai)) {
            SettingRow(
                title = stringResource(R.string.ai_settings_title),
                subtitle = aiSummary(state.aiActiveProfile),
                onClick = onOpenAi,
            )
        }

        // 9. 关于（版本信息，供用户参考；不可点击）
        SettingsGroupCard(icon = Icons.Outlined.Info, title = stringResource(R.string.settings_group_about)) {
            SettingRow(
                title = stringResource(R.string.settings_about_version),
                subtitle = "RICHWIN ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            SettingRow(
                title = stringResource(R.string.settings_about_build_time),
                subtitle = BuildConfig.BUILD_TIME,
            )
        }
    }

    if (showUsernameDialog) {
        SingleFieldDialog(
            title = stringResource(R.string.settings_change_username),
            label = stringResource(R.string.settings_new_username),
            confirmText = stringResource(R.string.common_save),
            onConfirm = { name -> showUsernameDialog = false; viewModel.changeUsername(name) },
            onDismiss = { showUsernameDialog = false },
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onConfirm = { old, new -> showPasswordDialog = false; viewModel.changePassword(old, new) },
            onDismiss = { showPasswordDialog = false },
        )
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            current = state.currentLanguage,
            onPick = { lang ->
                showLanguageDialog = false
                viewModel.setLanguage(lang)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            current = state.currentTheme,
            onPick = { theme ->
                showThemeDialog = false
                viewModel.setTheme(theme)
            },
            onDismiss = { showThemeDialog = false },
        )
    }
}

/** AI 摘要：「DeepSeek · deepseek-chat」或「未配置」；自定义档案优先显示档案名。 */
@Composable
private fun aiSummary(profile: AiProfile?): String {
    if (profile == null || !profile.isConfigured) {
        return stringResource(R.string.settings_ai_subtitle_not_configured)
    }
    val label = profile.displayName()
    return stringResource(R.string.settings_ai_subtitle_configured, "$label · ${profile.model}")
}

/** 档案显示名：自定义档案用自取名（空则回退「自定义」），预设档案用服务商资源名。 */
@Composable
internal fun AiProfile.displayName(): String = when {
    !isPreset && name.isNotBlank() -> name
    !isPreset -> stringResource(AiProviderPreset.CUSTOM.labelRes())
    else -> stringResource(provider.labelRes())
}

/** 主题选择对话框：列出 [AppTheme] 选项，当前选中打勾。 */
@Composable
private fun ThemePickerDialog(
    current: AppTheme,
    onPick: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_dialog_title)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    SettingRow(
                        title = stringResource(theme.labelRes),
                        onClick = { onPick(theme) },
                        trailing = {
                            if (theme == current) {
                                Text(
                                    stringResource(R.string.settings_current_badge),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                    )
                }
            }
        },
    )
}

/** 语言选择对话框：列出 [AppLanguage] 选项，当前选中打勾。 */
@Composable
private fun LanguagePickerDialog(
    current: AppLanguage,
    onPick: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_dialog_title)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        text = {
            Column {
                AppLanguage.entries.forEach { lang ->
                    SettingRow(
                        title = stringResource(lang.labelRes),
                        onClick = { onPick(lang) },
                        trailing = {
                            if (lang == current) {
                                Text(
                                    stringResource(R.string.settings_current_badge),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                    )
                }
            }
        },
    )
}
