package com.yingjing.pfa.ui.screens.overview

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.usecase.PortfolioSummary
import com.yingjing.pfa.ui.components.AssetPieChart
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

@Composable
fun OverviewScreen(viewModel: OverviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val summary = state.summary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("总览", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        NetWorthCard(summary)

        Spacer(Modifier.height(12.dp))
        TrendCard(state.trend)

        if (summary != null) {
            val slices = summary.byCategory
                .filter { it.category != AssetCategory.LIABILITY && it.amount > 0 }
                .map { PieSlice(it.category.displayName, it.amount, colorFor(it.category)) }
            if (slices.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("资产分布", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        AssetPieChart(slices, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "现价由行情每日自动更新（可在「我的」页立即刷新）；净值走势按每日快照累积。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NetWorthCard(summary: PortfolioSummary?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueLightMode, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Text("总资产净值", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            summary?.let { MoneyFormat.format(it.netWorth, it.displayCurrency) } ?: "—",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (summary != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    "总资产 ${MoneyFormat.format(summary.totalAssets, summary.displayCurrency)}",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "总负债 ${MoneyFormat.format(summary.totalLiabilities, summary.displayCurrency)}",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(trend: List<Double>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("净值走势", style = MaterialTheme.typography.titleMedium)
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
                    "数据积累中：每日自动记录一条净值，多用几天即可看到走势曲线。",
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
    AssetCategory.LIABILITY -> Cat2
}
