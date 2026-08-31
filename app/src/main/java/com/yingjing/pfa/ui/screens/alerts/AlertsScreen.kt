package com.yingjing.pfa.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.Alert
import com.yingjing.pfa.domain.model.AlertCategory
import com.yingjing.pfa.domain.model.AlertSeverity
import com.yingjing.pfa.ui.theme.Cat1
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

    Column(modifier = Modifier.fillMaxSize().background(pageBg).padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (alerts.any { !it.read }) {
                TextButton(onClick = viewModel::markAllRead) { Text(stringResource(R.string.alerts_mark_all_read)) }
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
                    AlertItem(alert, categoryNames[alert.category] ?: alert.category.name, onClick = { viewModel.markRead(alert.id) })
                }
            }
        }
    }
}

@Composable
private fun AlertItem(alert: Alert, categoryLabel: String, onClick: () -> Unit) {
    val alpha = if (alert.read) 0.55f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp, end = 10.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(severityColor(alert.severity).copy(alpha = alpha)),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
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
                Text(
                    formatTime(alert.createdAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
