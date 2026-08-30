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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.usecase.PortfolioSummary
import com.yingjing.pfa.ui.components.AssetPieChart
import com.yingjing.pfa.ui.components.MoneyText
import com.yingjing.pfa.ui.components.NetWorthTrendChart
import com.yingjing.pfa.ui.components.PieSlice
import com.yingjing.pfa.ui.format.MoneyFormat
import com.yingjing.pfa.ui.theme.BlueLightMode
import com.yingjing.pfa.ui.theme.Cat1
import com.yingjing.pfa.ui.theme.Cat2
import com.yingjing.pfa.ui.theme.Cat3
import com.yingjing.pfa.ui.theme.Cat4
import com.yingjing.pfa.ui.theme.Cat5
import com.yingjing.pfa.ui.theme.Cat6
import com.yingjing.pfa.ui.theme.Cat7
import com.yingjing.pfa.ui.theme.Cat8
import com.yingjing.pfa.ui.theme.Cat9

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
        TrendCard(state.trend, onOpenTrend)

        if (summary != null) {
            val slices = summary.byCategory
                .filter { it.category != AssetCategory.LIABILITY && it.amount > 0 }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueLightMode, RoundedCornerShape(20.dp))
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
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MoneyText(
                    stringResource(
                        R.string.overview_total_assets,
                        MoneyFormat.format(summary.totalAssets, symbol),
                    ),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                )
                MoneyText(
                    stringResource(
                        R.string.overview_total_liabilities,
                        MoneyFormat.format(summary.totalLiabilities, symbol),
                    ),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(trend: List<Double>, onOpenTrend: () -> Unit) {
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
                NetWorthTrendChart(
                    points = trend,
                    lineColor = BlueLightMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
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
