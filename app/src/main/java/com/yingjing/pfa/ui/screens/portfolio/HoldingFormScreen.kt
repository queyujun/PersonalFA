package com.yingjing.pfa.ui.screens.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.HousePriceCities

@Composable
fun HoldingFormScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: HoldingFormViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()
    val typeLabel = stringResource(s.type.displayRes)

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                stringResource(
                    if (s.isEdit) R.string.form_title_edit else R.string.form_title_add,
                    typeLabel,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(stringResource(R.string.form_label_name), s.name) { v -> viewModel.onField { copy(name = v) } }

            Text(stringResource(R.string.form_label_currency), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { c ->
                    FilterChip(
                        selected = s.currency == c,
                        onClick = { viewModel.onField { copy(currency = c) } },
                        label = { Text("${stringResource(c.symbolRes)} ${stringResource(c.labelRes)}") },
                    )
                }
            }

            when (s.type) {
                AssetType.A_SHARE, AssetType.HK_STOCK, AssetType.US_STOCK,
                AssetType.GOLD_ETF, AssetType.BOND_ETF, AssetType.CRYPTO -> {
                    Field(stringResource(R.string.form_label_symbol), s.symbol) { v -> viewModel.onField { copy(symbol = v) } }
                    Field(stringResource(R.string.form_label_quantity), s.quantity, KeyboardType.Number) { v -> viewModel.onField { copy(quantity = v) } }
                    Field(stringResource(R.string.form_label_cost_price), s.costPrice, KeyboardType.Number) { v -> viewModel.onField { copy(costPrice = v) } }
                    Field(stringResource(R.string.form_label_current_price), s.currentPrice, KeyboardType.Number) { v -> viewModel.onField { copy(currentPrice = v) } }
                }
                AssetType.PHYSICAL_GOLD -> {
                    Field(stringResource(R.string.form_label_grams), s.quantity, KeyboardType.Number) { v -> viewModel.onField { copy(quantity = v) } }
                    Field(stringResource(R.string.form_label_cost_per_gram), s.costPrice, KeyboardType.Number) { v -> viewModel.onField { copy(costPrice = v) } }
                    Field(stringResource(R.string.form_label_current_per_gram), s.currentPrice, KeyboardType.Number) { v -> viewModel.onField { copy(currentPrice = v) } }
                }
                AssetType.ACCOUNT_CASH ->
                    Field(stringResource(R.string.form_label_cash_amount), s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                AssetType.OTC_FUND -> {
                    Field(stringResource(R.string.form_label_symbol), s.symbol) { v -> viewModel.onField { copy(symbol = v) } }
                    Field(stringResource(R.string.form_label_shares), s.quantity, KeyboardType.Number) { v -> viewModel.onField { copy(quantity = v) } }
                    Field(stringResource(R.string.form_label_cost_nav), s.costPrice, KeyboardType.Number) { v -> viewModel.onField { copy(costPrice = v) } }
                    // 子分类：中国大陆（在线抓取净值，切换时清空手录净值）/ 其他（手录净值）
                    Text(stringResource(R.string.form_label_otc_region), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = s.autoFetchNav,
                            onClick = { viewModel.onField { copy(autoFetchNav = true, currentPrice = "") } },
                            label = { Text(stringResource(R.string.otc_region_mainland)) },
                        )
                        FilterChip(
                            selected = !s.autoFetchNav,
                            onClick = { viewModel.onField { copy(autoFetchNav = false) } },
                            label = { Text(stringResource(R.string.otc_region_other)) },
                        )
                    }
                    if (s.autoFetchNav) {
                        Text(
                            stringResource(R.string.form_otc_autonav_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Field(stringResource(R.string.form_label_current_nav), s.currentPrice, KeyboardType.Number) { v -> viewModel.onField { copy(currentPrice = v) } }
                    }
                }
                AssetType.REAL_ESTATE -> {
                    CityDropdown(s.city) { v -> viewModel.onField { copy(city = v) } }
                    Field(stringResource(R.string.form_label_area), s.areaSqm, KeyboardType.Number) { v -> viewModel.onField { copy(areaSqm = v) } }
                    Field(stringResource(R.string.form_label_current_value), s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.padding(end = 12.dp)) {
                            Text(stringResource(R.string.form_reit_estimate), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.form_reit_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = s.autoEstimate,
                            onCheckedChange = { v -> viewModel.onField { copy(autoEstimate = v) } },
                        )
                    }
                }
                AssetType.DEPOSIT -> {
                    Field(stringResource(R.string.form_label_principal), s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Field(stringResource(R.string.form_label_annual_rate), s.annualRate, KeyboardType.Number) { v -> viewModel.onField { copy(annualRate = v) } }
                    Field(stringResource(R.string.form_label_maturity), s.maturityDate) { v -> viewModel.onField { copy(maturityDate = v) } }
                    Field(stringResource(R.string.form_label_deposit_type), s.depositType) { v -> viewModel.onField { copy(depositType = v) } }
                }
                AssetType.EQUITY -> {
                    Field(stringResource(R.string.form_label_current_value), s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Field(stringResource(R.string.form_label_share_percent), s.sharePercent, KeyboardType.Number) { v -> viewModel.onField { copy(sharePercent = v) } }
                    Field(stringResource(R.string.form_label_equity_cost), s.costPrice, KeyboardType.Number) { v -> viewModel.onField { copy(costPrice = v) } }
                }
                AssetType.MISC -> {
                    Field(stringResource(R.string.form_label_current_value), s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Field(stringResource(R.string.form_label_note), s.note) { v -> viewModel.onField { copy(note = v) } }
                }
                AssetType.LIABILITY -> {
                    Field(stringResource(R.string.form_label_liability_type), s.liabilityType) { v -> viewModel.onField { copy(liabilityType = v) } }
                    Field(stringResource(R.string.form_label_owed), s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Field(stringResource(R.string.form_label_interest), s.annualRate, KeyboardType.Number) { v -> viewModel.onField { copy(annualRate = v) } }
                    Field(stringResource(R.string.form_label_monthly_payment), s.monthlyPayment, KeyboardType.Number) { v -> viewModel.onField { copy(monthlyPayment = v) } }
                    Field(stringResource(R.string.form_label_repayment_day), s.repaymentDay, KeyboardType.Number) { v -> viewModel.onField { copy(repaymentDay = v) } }
                }
            }

            s.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { viewModel.submit(onSaved) },
                enabled = !s.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(if (s.isEdit) R.string.form_save_edit else R.string.form_save))
            }
            Text(
                stringResource(R.string.form_price_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityDropdown(
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(selected) { mutableStateOf(selected) }
    val filtered = remember(query) {
        if (query.isBlank()) HousePriceCities.ALL
        else HousePriceCities.ALL.filter { it.contains(query.trim()) }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSelect(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.form_label_city)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            filtered.take(20).forEach { city ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(city) },
                    onClick = {
                        query = city
                        onSelect(city)
                        expanded = false
                    },
                )
            }
            if (filtered.isEmpty()) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.form_no_city_match)) },
                    onClick = { expanded = false },
                )
            }
        }
    }
}
