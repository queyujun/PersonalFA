package com.yingjing.pfa.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.foundation.layout.padding
import com.yingjing.pfa.R
import java.time.LocalDate

/**
 * 数据与同步二级界面：立即刷新行情 / 清理历史走势数据（日历+二次确认）/ 行情刷新计划。
 * 复用一级 [SettingsScreen] 的共享 [SettingsViewModel] 实例。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDetailScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.clearStatus() }

    var showPurgeDatePicker by remember { mutableStateOf(false) }
    var pendingPurgeDate by remember { mutableStateOf<LocalDate?>(null) }
    var showPurgeConfirm by remember { mutableStateOf(false) }

    SettingsDetailScaffold(title = stringResource(R.string.settings_group_sync), onBack = onBack) {
        SettingsGroupCard(icon = Icons.Outlined.Sync, title = stringResource(R.string.settings_group_sync)) {
            SettingRow(
                title = stringResource(R.string.sync_refresh_now),
                subtitle = stringResource(
                    R.string.sync_last_update,
                    state.lastSyncMs?.let { formatTime(it) } ?: stringResource(R.string.settings_not_updated),
                ),
                trailing = {
                    OutlinedButton(onClick = viewModel::refreshQuotesNow) { Text(stringResource(R.string.common_refresh)) }
                },
            )
            SettingRow(
                title = stringResource(R.string.sync_purge_title),
                subtitle = stringResource(R.string.sync_purge_subtitle),
                onClick = { showPurgeDatePicker = true },
            )
            state.purgeMessage?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SchedulePlanFields(
                subtitle = stringResource(R.string.sync_plan_subtitle),
                hint = stringResource(R.string.sync_plan_hint),
                intervalDays = state.syncIntervalDays,
                hour = state.syncHour,
                onSave = { d, h -> viewModel.setSyncSchedule(d, h) },
            )
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

    // 历史走势清理：① 日历选择边界日期 → ② 二次确认（红色删除按钮 + 不可恢复提示）。
    if (showPurgeDatePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPurgeDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        dateState.selectedDateMillis?.let { ms ->
                            pendingPurgeDate = LocalDate.ofEpochDay(ms / 86_400_000L)
                            showPurgeConfirm = true
                        }
                        showPurgeDatePicker = false
                    },
                ) { Text(stringResource(R.string.common_next)) }
            },
            dismissButton = { TextButton(onClick = { showPurgeDatePicker = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state = dateState) }
    }

    if (showPurgeConfirm && pendingPurgeDate != null) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false; pendingPurgeDate = null },
            title = { Text(stringResource(R.string.sync_purge_confirm_title, pendingPurgeDate.toString())) },
            text = {
                Text(stringResource(R.string.sync_purge_confirm_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPurgeDate?.let { viewModel.purgeSnapshotsBefore(it.toEpochDay()) }
                    showPurgeConfirm = false
                    pendingPurgeDate = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirm = false; pendingPurgeDate = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
