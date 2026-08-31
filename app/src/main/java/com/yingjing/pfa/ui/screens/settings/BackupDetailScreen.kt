package com.yingjing.pfa.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yingjing.pfa.R

/**
 * 数据备份二级界面：立即备份 / 从文件恢复 / 自动备份开关 + 计划 / 从本机自动备份恢复。
 * 复用一级 [SettingsScreen] 的共享 [SettingsViewModel] 实例。
 */
@Composable
fun BackupDetailScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.clearStatus() }

    var exportPass by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val backupFilenamePrefix = stringResource(R.string.backup_filename_prefix)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri, exportPass) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) { importUri = uri; showImportDialog = true } }

    SettingsDetailScaffold(title = stringResource(R.string.settings_group_backup), onBack = onBack) {
        SettingsGroupCard(icon = Icons.Outlined.CloudUpload, title = stringResource(R.string.settings_group_backup)) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = { exportPass = ""; showExportDialog = true }) { Text(stringResource(R.string.backup_now)) }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.backup_restore)) }
                }
            }
            SettingRow(
                title = stringResource(R.string.backup_auto_weekly),
                subtitle = stringResource(R.string.backup_auto_subtitle),
                trailing = {
                    Switch(
                        checked = state.autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackup(it) },
                    )
                },
            )
            // 本机自动备份状态 + 恢复入口（仅当备份文件存在时提供恢复按钮）
            if (state.autoBackupFileExists) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val lastBackup = state.lastBackupMs
                    Text(
                        if (lastBackup != null)
                            stringResource(R.string.backup_last_backup_at, formatTime(lastBackup))
                        else stringResource(R.string.backup_backup_file_exists),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { showRestoreConfirm = true }) {
                        Text(stringResource(R.string.backup_restore_auto))
                    }
                }
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    stringResource(R.string.backup_no_auto_backup),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            SchedulePlanFields(
                subtitle = stringResource(R.string.backup_plan_subtitle),
                hint = stringResource(R.string.backup_plan_hint),
                intervalDays = state.backupIntervalDays,
                hour = state.backupHour,
                onSave = { d, h -> viewModel.setBackupSchedule(d, h) },
            )
        }

        Text(
            stringResource(R.string.backup_security_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (showExportDialog) {
        PassphraseDialog(
            title = stringResource(R.string.backup_set_passphrase),
            confirmText = stringResource(R.string.backup_export),
            onConfirm = { pass ->
                exportPass = pass
                showExportDialog = false
                exportLauncher.launch("${backupFilenamePrefix}backup-${System.currentTimeMillis()}.pfa")
            },
            onDismiss = { showExportDialog = false },
        )
    }

    if (showImportDialog) {
        PassphraseDialog(
            title = stringResource(R.string.backup_input_passphrase),
            confirmText = stringResource(R.string.backup_restore),
            onConfirm = { pass ->
                showImportDialog = false
                importUri?.let { viewModel.importFrom(it, pass) }
            },
            onDismiss = { showImportDialog = false },
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.backup_restore_auto)) },
            text = { Text(stringResource(R.string.backup_restore_auto_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    viewModel.restoreFromAutoBackup()
                }) { Text(stringResource(R.string.backup_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
