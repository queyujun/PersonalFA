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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HoldingValue
import com.yingjing.pfa.domain.usecase.RealEstateEstimator
import com.yingjing.pfa.ui.format.MoneyFormat
import com.yingjing.pfa.ui.components.MoneyText
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
    val cdBack = stringResource(R.string.cd_back)
    val cdEdit = stringResource(R.string.cd_edit)
    val titleFallback = stringResource(R.string.detail_title_fallback)
    val loading = stringResource(R.string.common_loading)
    val edit = stringResource(R.string.common_edit)
    val delete = stringResource(R.string.common_delete)
    val cancel = stringResource(R.string.common_cancel)
    val pendingMarket = stringResource(R.string.detail_price_pending)
    val estimateSyncing = stringResource(R.string.detail_estimate_syncing)
    val otcPending = stringResource(R.string.detail_otc_pending)
    val reitIndex = stringResource(R.string.detail_reit_index)
    val confirmBody = stringResource(R.string.detail_delete_confirm_body)

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = cdBack)
            }
            Text(
                holding?.name ?: titleFallback,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            holding?.let { h ->
                IconButton(onClick = { onEdit(h.type, h.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = cdEdit)
                }
            }
        }

        val h = holding
        if (h == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(loading, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val currencySymbol = stringResource(h.currency.symbolRes)
        val currencyLabel = stringResource(h.currency.labelRes)
        val typeLabel = stringResource(h.type.displayRes)
        val value = HoldingValue.currentValue(h, now)
        val profit = HoldingValue.profit(h, now)
        val marketValueLabel = stringResource(R.string.detail_market_value, currencyLabel)

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(marketValueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(
                        MoneyFormat.format(value, currencySymbol),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    profit?.let {
                        MoneyText(
                            stringResource(R.string.detail_holding_profit, MoneyFormat.formatSigned(it, currencySymbol)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it >= 0) GainRed else LossGreen,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    detailRows(h, typeLabel, currencySymbol, currencyLabel, pendingMarket, estimateSyncing, otcPending, reitIndex)
                        .forEachIndexed { index, (label, valueText) ->
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
                ) { Text(edit) }
                OutlinedButton(
                    onClick = { showConfirm = true },
                    modifier = Modifier.weight(1f),
                ) { Text(delete, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showConfirm && holding != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.detail_delete_confirm_title, holding!!.name)) },
            text = { Text(confirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.delete(onDeleted)
                }) { Text(delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(cancel) }
            },
        )
    }
}

@Composable
private fun detailRows(
    h: Holding,
    typeLabel: String,
    currencySymbol: String,
    currencyLabel: String,
    pendingMarket: String,
    estimateSyncing: String,
    otcPending: String,
    reitIndex: String,
): List<Pair<String, String>> = buildList {
    add(stringResource(R.string.detail_label_type) to typeLabel)
    add(stringResource(R.string.detail_label_currency) to "$currencySymbol $currencyLabel")
    if (h.type.marketPriced) {
        if (h.type != AssetType.PHYSICAL_GOLD) {
            h.symbol?.let { add(stringResource(R.string.detail_label_symbol_name) to it) }
        }
        if (h.type == AssetType.PHYSICAL_GOLD) {
            h.quantity?.let { add(stringResource(R.string.detail_label_grams) to num(it)) }
            h.costPrice?.let { add(stringResource(R.string.detail_label_cost_per_gram) to num(it)) }
            add(stringResource(R.string.detail_label_current_per_gram) to (h.currentPrice?.let { num(it) } ?: pendingMarket))
        } else {
            h.quantity?.let { add(stringResource(R.string.detail_label_quantity) to num(it)) }
            h.costPrice?.let { add(stringResource(R.string.detail_label_cost_price) to num(it)) }
            add(stringResource(R.string.detail_label_current_price) to (h.currentPrice?.let { num(it) } ?: pendingMarket))
        }
    }
    when (h.type) {
        AssetType.DEPOSIT -> {
            add(stringResource(R.string.detail_label_principal) to num(h.manualValue))
            add(stringResource(R.string.detail_label_annual_rate) to "${num(h.annualRatePercent)}%")
        }
        AssetType.REAL_ESTATE -> {
            h.city?.let { add(stringResource(R.string.detail_label_city) to it) }
            h.areaSqm?.let { add(stringResource(R.string.detail_label_area) to "${num(it)} ㎡") }
            add(stringResource(R.string.detail_label_current_value) to num(h.manualValue))
            if (h.autoEstimate == true) {
                val base = h.valueBaseDateEpochMs?.let { monthLabel(it) }
                val adjust = RealEstateEstimator.cumulativeAdjustPercent(h.manualValue ?: 0.0, h.estimatedValue)
                add(stringResource(R.string.detail_label_estimate_method) to reitIndex)
                add(stringResource(R.string.detail_label_estimate_value) to (h.estimatedValue?.let { num(it) } ?: estimateSyncing))
                base?.let { add(stringResource(R.string.detail_label_base_date) to it) }
                adjust?.let { add(stringResource(R.string.detail_label_adjust) to "${if (it >= 0) "+" else ""}${"%.1f".format(it)}%") }
            }
        }
        AssetType.EQUITY -> {
            add(stringResource(R.string.detail_label_current_value) to num(h.manualValue))
            h.sharePercent?.let { add(stringResource(R.string.detail_label_share_percent) to "${num(it)}%") }
            h.costPrice?.let { add(stringResource(R.string.detail_label_equity_cost) to num(it)) }
        }
        AssetType.ACCOUNT_CASH -> add(stringResource(R.string.detail_label_cash_amount) to num(h.manualValue))
        AssetType.OTC_FUND -> {
            h.symbol?.let { add(stringResource(R.string.detail_label_symbol_name) to it) }
            h.quantity?.let { add(stringResource(R.string.detail_label_shares) to num(it)) }
            h.costPrice?.let { add(stringResource(R.string.detail_label_cost_nav) to num(it)) }
            // 子分类：中国大陆（在线抓取）/ 其他（手录），仅在详情页区分
            add(
                stringResource(R.string.detail_label_otc_region) to
                    stringResource(if (h.autoFetchNav == true) R.string.otc_region_mainland else R.string.otc_region_other),
            )
            add(stringResource(R.string.detail_label_current_nav) to (h.currentPrice?.let { num(it) } ?: otcPending))
        }
        AssetType.MISC -> {
            add(stringResource(R.string.detail_label_current_value) to num(h.manualValue))
            h.note?.let { add(stringResource(R.string.detail_label_note) to it) }
        }
        AssetType.LIABILITY -> {
            h.liabilityType?.let { add(stringResource(R.string.detail_label_liability_type) to it) }
            add(stringResource(R.string.detail_label_owed) to num(h.manualValue))
            h.annualRatePercent?.let { add(stringResource(R.string.detail_label_interest) to "${num(it)}%") }
            h.monthlyPayment?.let { add(stringResource(R.string.detail_label_monthly_payment) to num(it)) }
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
