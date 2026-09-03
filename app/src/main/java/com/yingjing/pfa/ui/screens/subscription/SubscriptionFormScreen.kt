package com.yingjing.pfa.ui.screens.subscription

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.BillingCycle
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.SubscriptionCategory
import androidx.compose.material3.FilterChip

/**
 * 订阅表单：名称 / 分类 / 金额+币种 / 周期 / 首扣日期 / 提醒 / 支付方式 / 备注 / 状态。
 * 编辑态额外提供删除。校验：名称非空、金额 > 0、日期 yyyy-MM-dd。
 */
@Composable
fun SubscriptionFormScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: SubscriptionFormViewModel = hiltViewModel(),
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                stringResource(if (s.isEdit) R.string.sub_form_title_edit else R.string.sub_form_title_add),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(stringResource(R.string.form_label_name), s.name) { v -> viewModel.onField { copy(name = v) } }

            Text(stringResource(R.string.sub_label_category), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubscriptionCategory.entries.forEach { c ->
                    FilterChip(
                        selected = s.category == c,
                        onClick = { viewModel.onField { copy(category = c) } },
                        label = { Text(stringResource(c.displayRes)) },
                    )
                }
            }

            Field(
                stringResource(R.string.sub_label_amount),
                s.amount,
                KeyboardType.Number,
            ) { v -> viewModel.onField { copy(amount = v) } }

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

            Text(stringResource(R.string.sub_label_cycle), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BillingCycle.entries.forEach { c ->
                    FilterChip(
                        selected = s.cycle == c,
                        onClick = { viewModel.onField { copy(cycle = c) } },
                        label = { Text(stringResource(c.displayRes)) },
                    )
                }
            }

            Field(
                stringResource(R.string.sub_label_first_bill),
                s.firstBillDate,
            ) { v -> viewModel.onField { copy(firstBillDate = v) } }

            Text(stringResource(R.string.sub_label_reminder), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 3, 7).forEach { days ->
                    FilterChip(
                        selected = s.reminderDaysBefore == days,
                        onClick = { viewModel.onField { copy(reminderDaysBefore = days) } },
                        label = {
                            Text(
                                if (days == 0) stringResource(R.string.sub_reminder_off)
                                else stringResource(R.string.sub_reminder_days, days),
                            )
                        },
                    )
                }
            }

            Field(
                stringResource(R.string.sub_label_payment_method),
                s.paymentMethod,
            ) { v -> viewModel.onField { copy(paymentMethod = v) } }

            Field(stringResource(R.string.form_label_note), s.note) { v -> viewModel.onField { copy(note = v) } }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.sub_label_active), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = s.active,
                    onCheckedChange = { v -> viewModel.onField { copy(active = v) } },
                )
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
            if (s.isEdit) {
                TextButton(
                    onClick = { viewModel.delete(onSaved) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.sub_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                stringResource(R.string.sub_form_date_hint),
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
