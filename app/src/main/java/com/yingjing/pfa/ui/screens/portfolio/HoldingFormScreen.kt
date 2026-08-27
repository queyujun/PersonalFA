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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                "${if (s.isEdit) "编辑" else "添加"}${s.type.displayName}",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field("名称", s.name) { v -> viewModel.onField { copy(name = v) } }

            Text("计价货币", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { c ->
                    FilterChip(
                        selected = s.currency == c,
                        onClick = { viewModel.onField { copy(currency = c) } },
                        label = { Text("${c.symbol} ${c.label}") },
                    )
                }
            }

            when (s.type) {
                AssetType.A_SHARE, AssetType.HK_STOCK, AssetType.US_STOCK,
                AssetType.GOLD_ETF, AssetType.BOND_ETF, AssetType.CRYPTO -> {
                    Field("代码 / 名称", s.symbol) { v -> viewModel.onField { copy(symbol = v) } }
                    Field("持有数量", s.quantity, KeyboardType.Number) { v -> viewModel.onField { copy(quantity = v) } }
                    Field("成本价（可选）", s.costPrice, KeyboardType.Number) { v -> viewModel.onField { copy(costPrice = v) } }
                    Field("现价（可选，P3 起自动更新）", s.currentPrice, KeyboardType.Number) { v -> viewModel.onField { copy(currentPrice = v) } }
                }
                AssetType.ACCOUNT_CASH ->
                    Field("现金金额", s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                AssetType.REAL_ESTATE -> {
                    CityDropdown(s.city) { v -> viewModel.onField { copy(city = v) } }
                    Field("建筑面积 ㎡（可选）", s.areaSqm, KeyboardType.Number) { v -> viewModel.onField { copy(areaSqm = v) } }
                    Field("当前估值", s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.padding(end = 12.dp)) {
                            Text("按 70 城二手住宅指数自动估算", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "以录入值为基准，按每月二手住宅环比指数自动微调，同步后更新",
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
                    Field("本金金额", s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Field("年化利率 %", s.annualRate, KeyboardType.Number) { v -> viewModel.onField { copy(annualRate = v) } }
                    Field("到期日 yyyy-MM-dd（可选，用于到期提醒）", s.maturityDate) { v -> viewModel.onField { copy(maturityDate = v) } }
                    Field("存款类型（可选，如定期）", s.depositType) { v -> viewModel.onField { copy(depositType = v) } }
                }
                AssetType.EQUITY -> {
                    Field("当前估值", s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Field("持股比例 %（可选）", s.sharePercent, KeyboardType.Number) { v -> viewModel.onField { copy(sharePercent = v) } }
                    Field("入股成本（可选）", s.costPrice, KeyboardType.Number) { v -> viewModel.onField { copy(costPrice = v) } }
                }
                AssetType.LIABILITY -> {
                    Field("负债类型（可选，如房贷）", s.liabilityType) { v -> viewModel.onField { copy(liabilityType = v) } }
                    Field("欠款金额", s.manualValue, KeyboardType.Number) { v -> viewModel.onField { copy(manualValue = v) } }
                    Field("年利率 %（可选）", s.annualRate, KeyboardType.Number) { v -> viewModel.onField { copy(annualRate = v) } }
                    Field("每月还款本金（可选）", s.monthlyPayment, KeyboardType.Number) { v -> viewModel.onField { copy(monthlyPayment = v) } }
                    Field("每月还款日 1-31（可选，留空=每月最后一天）", s.repaymentDay, KeyboardType.Number) { v -> viewModel.onField { copy(repaymentDay = v) } }
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
                Text(if (s.isEdit) "保存修改" else "保 存")
            }
            Text(
                "提示：股票 / ETF / 代币的现价将在 P3（行情接口）起每日自动更新。",
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
            label = { Text("所在城市") },
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
                    text = { Text("无匹配城市") },
                    onClick = { expanded = false },
                )
            }
        }
    }
}
