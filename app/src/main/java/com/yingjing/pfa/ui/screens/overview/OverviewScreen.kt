package com.yingjing.pfa.ui.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.usecase.PortfolioSummary
import com.yingjing.pfa.ui.components.AssetPieChart
import com.yingjing.pfa.ui.components.MoneyText
import com.yingjing.pfa.ui.components.NetWorthTrendChart
import com.yingjing.pfa.ui.components.PieSlice
import com.yingjing.pfa.ui.components.StackedAreaSeries
import com.yingjing.pfa.ui.components.StackedAreaTrendChart
import com.yingjing.pfa.ui.format.MoneyFormat
import com.yingjing.pfa.ui.theme.Cat1
import com.yingjing.pfa.ui.theme.Cat2
import com.yingjing.pfa.ui.theme.Cat3
import com.yingjing.pfa.ui.theme.Cat4
import com.yingjing.pfa.ui.theme.Cat5
import com.yingjing.pfa.ui.theme.Cat6
import com.yingjing.pfa.ui.theme.Cat7
import com.yingjing.pfa.ui.theme.Cat8
import com.yingjing.pfa.ui.theme.Cat9
import com.yingjing.pfa.ui.theme.LocalBrandColors

@Composable
fun OverviewScreen(
    onOpenTrend: () -> Unit = {},
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val summary = state.summary
    val categoryNames = AssetCategory.entries.associateWith { stringResource(it.displayRes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.overview_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        NetWorthCard(summary)

        Spacer(Modifier.height(12.dp))
        TrendCard(state, onOpenTrend)

        if (summary != null) {
            val slices = summary.byCategory
                .filter { it.category != AssetCategory.LIABILITY && it.amount > 0 }
                .sortedByDescending { it.amount }
                .map { PieSlice(categoryNames[it.category] ?: it.category.name, it.amount, colorFor(it.category)) }
            if (slices.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.overview_asset_distribution), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        AssetPieChart(
                            slices = slices,
                            currencySymbol = stringResource(summary.displayCurrency.symbolRes),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.overview_price_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NetWorthCard(summary: PortfolioSummary?) {
    val symbol = summary?.displayCurrency?.let { stringResource(it.symbolRes) } ?: ""
    val brand = LocalBrandColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(brand.gradientStart, brand.gradientEnd)), RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.overview_net_worth), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        MoneyText(
            summary?.let { MoneyFormat.format(it.netWorth, symbol) } ?: "—",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (summary != null) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NetWorthBreakdownRow(
                    label = stringResource(R.string.overview_total_assets),
                    amount = MoneyFormat.formatFixed2(summary.totalAssets, symbol),
                )
                NetWorthBreakdownRow(
                    label = stringResource(R.string.overview_total_liabilities),
                    amount = MoneyFormat.formatFixed2(summary.totalLiabilities, symbol),
                )
            }
        }
    }
}

/**
 * 总资产 / 总负债的一行：左标签 + 右金额。两行共用此布局，金额用等宽字体
 * 且右对齐到同一右边缘，使两个数字的小数位、个位纵向对齐（数字低位对齐）。
 * 标签与数字分置两端，避免数字过大时一行放不下。
 */
@Composable
private fun NetWorthBreakdownRow(
    label: String,
    amount: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodySmall,
        )
        MoneyText(
            amount,
            modifier = Modifier.padding(start = 12.dp),
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TrendCard(state: OverviewUiState, onOpenTrend: () -> Unit) {
    val trend = state.trend
    val stacked = state.stackedTrend
    val categoryNames = AssetCategory.entries.associateWith { stringResource(it.displayRes) }

    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenTrend() }) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.overview_trend), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.overview_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            if (trend.size >= 2) {
                // 堆叠面积图：按资产类别分层着色，顶部包络线=总资产走势。
                val series = stacked.series
                    .filter { it.values.any { v -> v > 0.0 } }
                    .map { s ->
                        StackedAreaSeries(
                            name = categoryNames[s.category] ?: s.category.name,
                            color = colorFor(s.category),
                            values = s.values,
                        )
                    }
                if (series.isNotEmpty()) {
                    StackedAreaTrendChart(
                        series = series,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    )
                } else {
                    // 分类快照尚未积累，退回单线总净值。
                    NetWorthTrendChart(
                        points = trend,
                        lineColor = LocalBrandColors.current.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    )
                }
            } else {
                Text(
                    stringResource(R.string.trend_accumulating_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun colorFor(category: AssetCategory): Color = when (category) {
    AssetCategory.REAL_ESTATE -> Cat1
    AssetCategory.DEPOSIT -> Cat2
    AssetCategory.STOCK -> Cat3
    AssetCategory.GOLD -> Cat4
    AssetCategory.BOND -> Cat5
    AssetCategory.CRYPTO -> Cat6
    AssetCategory.EQUITY -> Cat7
    AssetCategory.OTC_FUND -> Cat8
    AssetCategory.MISC -> Cat9
    AssetCategory.LIABILITY -> Cat2
}
