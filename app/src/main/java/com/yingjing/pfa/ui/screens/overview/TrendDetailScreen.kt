package com.yingjing.pfa.ui.screens.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.usecase.TREND_TOTAL_ID
import com.yingjing.pfa.domain.usecase.TimeGranularity
import com.yingjing.pfa.domain.usecase.TrendSeriesBuilder
import com.yingjing.pfa.ui.components.MultiSeriesTrendChart
import com.yingjing.pfa.ui.theme.BlueLightMode
import com.yingjing.pfa.ui.theme.Cat1
import com.yingjing.pfa.ui.theme.Cat2
import com.yingjing.pfa.ui.theme.Cat3
import com.yingjing.pfa.ui.theme.Cat4
import com.yingjing.pfa.ui.theme.Cat5
import com.yingjing.pfa.ui.theme.Cat6
import com.yingjing.pfa.ui.theme.Cat7
import com.yingjing.pfa.ui.theme.GainRed

@Composable
fun TrendDetailScreen(
    onBack: () -> Unit,
    viewModel: TrendDetailViewModel = hiltViewModel(),
) {
    val raw by viewModel.raw.collectAsState()
    var granularity by remember { mutableStateOf(TimeGranularity.DAY) }
    var selected by remember { mutableStateOf(setOf(TREND_TOTAL_ID)) }

    val available = remember(raw) { listOf(TREND_TOTAL_ID) + raw.categories.map { it.category }.distinct() }
    val selectedIds = available.filter { it in selected }.ifEmpty { listOf(TREND_TOTAL_ID) }
    val chartData = remember(raw, selectedIds, granularity) {
        TrendSeriesBuilder.build(raw.totals, raw.categories, selectedIds, granularity) { seriesLabel(it) }
    }
    val colors = selectedIds.map { seriesColor(it) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("净值走势（单位：万元）", style = MaterialTheme.typography.titleLarge)
        }

        // 时间粒度
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeGranularity.entries.forEach { g ->
                FilterChip(
                    selected = granularity == g,
                    onClick = { granularity = g },
                    label = { Text(g.label) },
                )
            }
        }

        // 资产类别多选（总净值 + 各类别）
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            available.forEach { id ->
                FilterChip(
                    selected = id in selectedIds,
                    onClick = {
                        selected = if (id in selected) (selected - id) else (selected + id)
                        if (selected.none { it in available }) selected = setOf(TREND_TOTAL_ID)
                    },
                    label = { Text(seriesLabelWithTotal(id)) },
                )
            }
        }

        if (chartData.bucketLabels.size < 2) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "数据积累中：每日自动记录一条，多用几天后即可查看走势、切换日/月/年并缩放。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            MultiSeriesTrendChart(
                data = chartData,
                colors = colors,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                chartData.series.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(9.dp)) { drawCircle(colors.getOrElse(i) { Color.Gray }) }
                        Text("  ${s.name}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                "双指缩放 · 拖动查看；横屏可看更大图",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun seriesLabel(categoryName: String): String =
    runCatching { AssetCategory.valueOf(categoryName).displayName }.getOrDefault(categoryName)

private fun seriesLabelWithTotal(id: String): String =
    if (id == TREND_TOTAL_ID) "总净值" else seriesLabel(id)

private fun seriesColor(id: String): Color {
    if (id == TREND_TOTAL_ID) return BlueLightMode
    return when (runCatching { AssetCategory.valueOf(id) }.getOrNull()) {
        AssetCategory.REAL_ESTATE -> Cat1
        AssetCategory.DEPOSIT -> Cat2
        AssetCategory.STOCK -> Cat3
        AssetCategory.GOLD -> Cat4
        AssetCategory.BOND -> Cat5
        AssetCategory.CRYPTO -> Cat6
        AssetCategory.EQUITY -> Cat7
        AssetCategory.LIABILITY -> GainRed
        null -> Color.Gray
    }
}
