package com.yingjing.pfa.ui.screens.sentiment

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.FearScale
import com.yingjing.pfa.domain.usecase.FearIndexStats
import com.yingjing.pfa.domain.usecase.FearRange
import com.yingjing.pfa.ui.components.MoneyText
import com.yingjing.pfa.ui.theme.LocalBrandColors
import com.yingjing.pfa.ui.theme.LocalDarkTheme
import com.yingjing.pfa.ui.theme.LocalSemanticColors
import com.yingjing.pfa.ui.theme.SemanticColors

/** 单个恐慌指数详情：当前值 → 历史位置三档分位 → 双轴走势（区间切换 + 价格叠加）→ 区间统计 → 解读。 */
@Composable
fun FearIndexDetailScreen(
    onBack: () -> Unit,
    viewModel: FearIndexDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val title = state.id?.let { stringResource(it.nameRes) } ?: stringResource(R.string.fear_title)

    LaunchedEffect(Unit) { viewModel.refreshIfStale() }
    LaunchedEffect(viewModel) {
        viewModel.refreshEvents.collect { snackbar.showSnackbar(refreshMessage(context, it)) }
    }

    Box(Modifier.fillMaxSize().background(LocalBrandColors.current.pageBackground)) {
        Column(Modifier.fillMaxSize()) {
            FearTopBar(title, state.refreshing, onBack, viewModel::refreshNow)
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            val content = state.content
            when {
                !state.loaded -> CenterMessage(stringResource(R.string.common_loading), loading = true)
                content == null && state.refreshing -> CenterMessage(stringResource(R.string.fear_loading_first), loading = true)
                content == null -> EmptyState(onRetry = viewModel::refreshNow)
                else -> DetailBody(
                    content = content,
                    showOverlay = state.showOverlay,
                    stale = state.stale,
                    onRange = viewModel::selectRange,
                    onToggleOverlay = viewModel::toggleOverlay,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DetailBody(
    content: FearDetailContent,
    showOverlay: Boolean,
    stale: Boolean,
    onRange: (FearRange) -> Unit,
    onToggleOverlay: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderCard(content, stale, semantic)
        PositionCard(content, semantic)
        ChartCard(content, showOverlay, onRange, onToggleOverlay, semantic)
        content.rangeStats?.let { StatsCard(content) }
        ExplainCard(content)
        Text(
            stringResource(if (content.id.scale == FearScale.FEAR_GREED) R.string.fear_source_fng else R.string.fear_source_cboe) +
                "\n" + stringResource(R.string.fear_source_price),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun HeaderCard(content: FearDetailContent, stale: Boolean, semantic: SemanticColors) {
    val s = content.summary
    val id = content.id
    val isFng = id.scale == FearScale.FEAR_GREED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(id.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(id.subtitleRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    LevelPill(stringResource(s.zone?.labelRes ?: s.level.labelRes), s.level.color(semantic))
                    if (stale) {
                        Spacer(Modifier.height(4.dp))
                        LevelPill(stringResource(R.string.fear_stale_badge), MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(FearFormat.value(s.latest.value, id.decimals), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                if (isFng) Text("/100", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    ChangeText(s.change, s.changePct, id.decimals)
                    Text(
                        stringResource(R.string.fear_change_vs_prev) + " · " + FearFormat.day(s.latest.epochDay),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isFng) {
                Spacer(Modifier.height(12.dp))
                FearGreedGauge(s.latest.value, semantic)
                Text(
                    stringResource(R.string.fng_zone_legend),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PositionCard(content: FearDetailContent, semantic: SemanticColors) {
    val isFng = content.id.scale == FearScale.FEAR_GREED
    val s = content.summary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fear_position_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LevelPill(stringResource(s.zone?.labelRes ?: s.level.labelRes), s.level.color(semantic))
            }
            content.positions.forEach { row ->
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        when (row.window) {
                            PositionWindow.Y1 -> stringResource(R.string.fear_window_1y)
                            PositionWindow.Y5 -> stringResource(R.string.fear_window_5y)
                            PositionWindow.ALL -> stringResource(R.string.fear_window_all, content.sinceYear)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyText(
                        stringResource(R.string.fear_window_pct, FearFormat.pct(row.stats.percentile)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                PercentileTrack(row.stats.percentile, semantic, reversed = isFng)
            }
            Spacer(Modifier.height(10.dp))
            val y1 = FearFormat.pct(s.oneYear.percentile)
            Text(
                if (isFng) stringResource(R.string.fear_position_note_fng, FearFormat.pct(100.0 - s.oneYear.percentile))
                else stringResource(R.string.fear_position_note_vol, y1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartCard(
    content: FearDetailContent,
    showOverlay: Boolean,
    onRange: (FearRange) -> Unit,
    onToggleOverlay: () -> Unit,
    semantic: SemanticColors,
) {
    val id = content.id
    val isFng = id.scale == FearScale.FEAR_GREED
    val overlayName = stringResource(id.overlay.labelRes)
    val primaryName = stringResource(id.nameRes)
    val colors = chartColors(semantic)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                stringResource(R.string.fear_chart_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FearRange.entries.forEach { r ->
                    FilterChip(
                        selected = content.range == r,
                        onClick = { onRange(r) },
                        label = { Text(stringResource(r.labelRes), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendSwatch(colors.overlay)
                Text(
                    stringResource(R.string.fear_overlay_toggle, overlayName),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                Switch(checked = showOverlay, onCheckedChange = { onToggleOverlay() })
            }
            val chart = content.chart
            if (chart == null) {
                Text(
                    stringResource(R.string.fear_chart_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                FearHistoryChart(
                    data = chart,
                    isFearGreed = isFng,
                    showOverlay = showOverlay,
                    primaryDecimals = id.decimals,
                    overlayDecimals = id.overlay.decimals,
                    colors = colors,
                    description = stringResource(R.string.fear_chart_title) + " · " + primaryName + (if (showOverlay) " · $overlayName" else ""),
                    modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendSwatch(colors.primary)
                        LegendText(stringResource(R.string.fear_axis_left, primaryName))
                        if (showOverlay && chart.overlay.size >= 2) {
                            Spacer(Modifier.width(12.dp))
                            LegendSwatch(colors.overlay)
                            LegendText(stringResource(R.string.fear_axis_right, overlayName))
                        }
                    }
                    if (!isFng && chart.median != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendSwatch(colors.band.copy(alpha = 1f).copy(alpha = 0.6f))
                            LegendText(stringResource(R.string.fear_legend_band_vol))
                            Spacer(Modifier.width(12.dp))
                            LegendSwatch(colors.median)
                            LegendText(stringResource(R.string.fear_legend_median, FearFormat.value(chart.median, id.decimals)))
                        }
                    }
                    if (showOverlay) {
                        val r = content.correlation
                        Text(
                            if (r == null) stringResource(R.string.fear_corr_na)
                            else stringResource(
                                R.string.fear_corr_line,
                                FearFormat.corr(r),
                                stringResource(
                                    R.string.fear_corr_desc,
                                    stringResource(FearIndexStats.correlationStrength(r).labelRes),
                                    stringResource(if (r < 0) R.string.corr_negative else R.string.corr_positive),
                                ),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(content: FearDetailContent) {
    val stats = content.rangeStats ?: return
    val d = if (content.id.scale == FearScale.FEAR_GREED) 1 else content.id.decimals
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.fear_stats_title) + " · " + stringResource(content.range.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.fear_stats_days, stats.count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.fear_stat_max), FearFormat.value(stats.max, d), Modifier.weight(1f))
                StatTile(stringResource(R.string.fear_stat_min), FearFormat.value(stats.min, d), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.fear_stat_mean), FearFormat.value(stats.mean, d), Modifier.weight(1f))
                StatTile(stringResource(R.string.fear_stat_median), FearFormat.value(stats.median, d), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExplainCard(content: FearDetailContent) {
    val name = stringResource(content.id.nameRes)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.fear_explain_title, name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(content.id.explainRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(Modifier.size(width = 12.dp, height = 4.dp).background(color, RoundedCornerShape(2.dp)))
}

@Composable
private fun LegendText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun chartColors(semantic: SemanticColors): FearChartColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val zoneAlpha = if (dark) 0.16f else 0.12f
    return FearChartColors(
        primary = LocalBrandColors.current.primary,
        overlay = semantic.overlayLine,
        axisText = scheme.onSurfaceVariant,
        grid = scheme.outline,
        band = semantic.fearVeryLow.copy(alpha = if (dark) 0.14f else 0.10f),
        median = scheme.onSurfaceVariant.copy(alpha = 0.7f),
        crosshair = scheme.onSurfaceVariant.copy(alpha = 0.6f),
        bubbleBg = scheme.inverseSurface.copy(alpha = 0.92f),
        bubbleText = scheme.inverseOnSurface,
        zoneFills = listOf(
            semantic.fearVeryHigh.copy(alpha = zoneAlpha),
            semantic.fearHigh.copy(alpha = zoneAlpha * 0.7f),
            semantic.fearNeutral.copy(alpha = zoneAlpha * 0.7f),
            semantic.fearLow.copy(alpha = zoneAlpha * 0.7f),
            semantic.fearVeryLow.copy(alpha = zoneAlpha),
        ),
    )
}
