package com.yingjing.pfa.ui.screens.alerts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.Alert
import com.yingjing.pfa.domain.model.AlertCategory
import com.yingjing.pfa.domain.model.AlertSeverity
import com.yingjing.pfa.ui.theme.Cat2
import com.yingjing.pfa.ui.theme.Cat4
import com.yingjing.pfa.ui.theme.LocalBrandColors

@Composable
fun AlertsScreen(viewModel: AlertsViewModel = hiltViewModel()) {
    val alerts by viewModel.alerts.collectAsState()
    var filter by remember { mutableStateOf<AlertCategory?>(null) }
    val filtered = filter?.let { c -> alerts.filter { it.category == c } } ?: alerts
    val categoryNames = AlertCategory.entries.associateWith { stringResource(it.displayRes) }
    val pageBg = LocalBrandColors.current.pageBackground

    // 多选模式：selectedIds 非空即进入（空集但 selectionMode=true 表示已进入尚未勾选）。
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 删除确认对话框（null=不显示）：负数标记 = 全部删除，正数 = 批量删除条数。
    var pendingDeleteCount by remember { mutableStateOf<Int?>(null) }
    val isDeleteAll = pendingDeleteCount != null && pendingDeleteCount!! < 0

    Column(modifier = Modifier.fillMaxSize().background(pageBg).padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (selectionMode) {
                // 多选顶栏：退出 + 已选计数 + 批量删除
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { exitSelection() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.common_cancel),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        stringResource(R.string.alerts_selected_count, selectedIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(
                    onClick = { pendingDeleteCount = selectedIds.size },
                    enabled = selectedIds.isNotEmpty(),
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            } else {
                Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row {
                    if (alerts.any { !it.read }) {
                        TextButton(onClick = viewModel::markAllRead) { Text(stringResource(R.string.alerts_mark_all_read)) }
                    }
                    if (alerts.isNotEmpty()) {
                        TextButton(onClick = { pendingDeleteCount = -1 }) {
                            Text(stringResource(R.string.alerts_delete_all), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.alerts_filter_all)) })
            AlertCategory.entries.forEach { c ->
                FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(categoryNames[c] ?: c.name) })
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.alerts_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { alert ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.delete(alert.id)
                                true
                            } else false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = { DeleteSwipeBackground() },
                    ) {
                        AlertItem(
                            alert = alert,
                            categoryLabel = categoryNames[alert.category] ?: alert.category.name,
                            selectionMode = selectionMode,
                            selected = alert.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (alert.id in selectedIds) selectedIds - alert.id else selectedIds + alert.id
                                } else {
                                    viewModel.markRead(alert.id)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds = setOf(alert.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // 批量/全部删除确认
    pendingDeleteCount?.let { count ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCount = null },
            title = { Text(stringResource(R.string.alerts_delete_confirm_title)) },
            text = {
                Text(
                    if (isDeleteAll) stringResource(R.string.alerts_delete_all_confirm_body)
                    else stringResource(R.string.alerts_delete_selected_confirm_body, count),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isDeleteAll) viewModel.deleteAll() else viewModel.deleteSelected(selectedIds.toList())
                    pendingDeleteCount = null
                    exitSelection()
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCount = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** 滑动删除背景（红色 + 删除图标，右对齐）。 */
@Composable
private fun DeleteSwipeBackground() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(end = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.common_delete),
            tint = MaterialTheme.colorScheme.onError,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlertItem(
    alert: Alert,
    categoryLabel: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val alpha = if (alert.read) 0.55f else 1f
    // 选中底色：在页面底色上叠加一层灰（浅色模式压深、深色模式提亮），对比可辨但不抢眼。
    val pageBg = LocalBrandColors.current.pageBackground
    val selectionBg = if (isSystemInDarkTheme()) {
        lerp(pageBg, Color.White, 0.10f)
    } else {
        lerp(pageBg, Color.Black, 0.08f)
    }
    // 条目必须不透明（正常=页面底色，选中=叠加灰），否则滑动删除的红色背景会透出来。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) selectionBg else pageBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
        }
        Box(
            modifier = Modifier
                .padding(top = 5.dp, end = 10.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(severityColor(alert.severity).copy(alpha = alpha)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(
                    "  ${alert.title}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    modifier = Modifier.weight(1f),
                )
                if (!selectionMode) {
                    Text(
                        formatTime(alert.createdAtEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                alert.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun severityColor(severity: AlertSeverity): Color = when (severity) {
    AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
    AlertSeverity.WARNING -> Cat4
    AlertSeverity.SERIOUS -> Cat2
}

private fun formatTime(epochMs: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
