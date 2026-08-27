package com.yingjing.pfa.ui.screens.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HoldingValue
import com.yingjing.pfa.domain.usecase.RealEstateEstimator
import com.yingjing.pfa.ui.format.MoneyFormat
import com.yingjing.pfa.ui.theme.GainRed
import com.yingjing.pfa.ui.theme.LossGreen

@Composable
fun HoldingDetailScreen(
    onEdit: (AssetType, Long) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: HoldingDetailViewModel = hiltViewModel(),
) {
    val holding by viewModel.holding.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                holding?.name ?: "详情",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            holding?.let { h ->
                IconButton(onClick = { onEdit(h.type, h.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
            }
        }

        val h = holding
        if (h == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val value = HoldingValue.currentValue(h, now)
        val profit = HoldingValue.profit(h, now)

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前市值（${h.currency.label}）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        MoneyFormat.format(value, h.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    profit?.let {
                        Text(
                            "持仓收益 ${MoneyFormat.formatSigned(it, h.currency)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it >= 0) GainRed else LossGreen,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    detailRows(h).forEachIndexed { index, (label, valueText) ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(valueText)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onEdit(h.type, h.id) },
                    modifier = Modifier.weight(1f),
                ) { Text("编辑") }
                OutlinedButton(
                    onClick = { showConfirm = true },
                    modifier = Modifier.weight(1f),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showConfirm && holding != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("删除「${holding!!.name}」？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.delete(onDeleted)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            },
        )
    }
}

private fun detailRows(h: Holding): List<Pair<String, String>> = buildList {
    add("类型" to h.type.displayName)
    add("计价货币" to "${h.currency.symbol} ${h.currency.label}")
    if (h.type.marketPriced) {
        h.symbol?.let { add("代码 / 名称" to it) }
        h.quantity?.let { add("持有数量" to num(it)) }
        h.costPrice?.let { add("成本价" to num(it)) }
        add("现价" to (h.currentPrice?.let { num(it) } ?: "待接入行情"))
    }
    when (h.type) {
        AssetType.DEPOSIT -> {
            add("本金" to num(h.manualValue))
            add("年化利率" to "${num(h.annualRatePercent)}%")
        }
        AssetType.REAL_ESTATE -> {
            h.city?.let { add("城市" to it) }
            h.areaSqm?.let { add("建筑面积" to "${num(it)} ㎡") }
            add("当前估值" to num(h.manualValue))
            if (h.autoEstimate == true) {
                val base = h.valueBaseDateEpochMs?.let { monthLabel(it) }
                val adjust = RealEstateEstimator.cumulativeAdjustPercent(h.manualValue ?: 0.0, h.estimatedValue)
                add("估算方式" to "70 城二手住宅指数")
                add("估算现值" to (h.estimatedValue?.let { num(it) } ?: "同步后更新"))
                base?.let { add("录入基准" to it) }
                adjust?.let { add("累计调整" to "${if (it >= 0) "+" else ""}${"%.1f".format(it)}%") }
            }
        }
        AssetType.EQUITY -> {
            add("当前估值" to num(h.manualValue))
            h.sharePercent?.let { add("持股比例" to "${num(it)}%") }
            h.costPrice?.let { add("入股成本" to num(it)) }
        }
        AssetType.ACCOUNT_CASH -> add("现金金额" to num(h.manualValue))
        AssetType.LIABILITY -> {
            h.liabilityType?.let { add("负债类型" to it) }
            add("欠款金额" to num(h.manualValue))
            h.annualRatePercent?.let { add("年利率" to "${num(it)}%") }
            h.monthlyPayment?.let { add("月供" to num(it)) }
        }
        else -> Unit
    }
}

private fun num(v: Double?): String {
    v ?: return "-"
    return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

/** 时间戳 → "yyyy-MM" 月份标签（详情页录入基准展示）。 */
private fun monthLabel(epochMs: Long): String = RealEstateEstimator.monthKey(epochMs)
