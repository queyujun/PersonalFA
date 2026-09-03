package com.yingjing.pfa.ui.screens.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.Subscription
import com.yingjing.pfa.ui.format.MoneyFormat
import com.yingjing.pfa.ui.theme.LocalBrandColors

/**
 * 订阅管理主页：汇总卡（月均 + 年化，换算到默认展示币种）→ 筛选行 → 即将续费分区 → 全部订阅列表。
 * 行内以订阅自身计费币种展示「$10/月 · $120/年」双价（按周期折算，不换汇）。
 */
@Composable
fun SubscriptionScreen(
    onOpenEdit: (Long) -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(SubscriptionFilter.ALL) }
    val pageBg = LocalBrandColors.current.pageBackground
    val symbol = stringResource(state.displayCurrency.symbolRes)

    val filtered = remember(state.subscriptions, filter) {
        when (filter) {
            SubscriptionFilter.ALL -> state.subscriptions
            SubscriptionFilter.ACTIVE -> state.subscriptions.filter { it.active }
            SubscriptionFilter.INACTIVE -> state.subscriptions.filter { !it.active }
        }
    }
    // 即将续费：进行中的订阅按续费日升序，最多 3 条。
    val upcoming = remember(state.subscriptions) {
        state.subscriptions.filter { it.active }
            .sortedBy { it.nextRenewalEpochMs }
            .take(3)
    }

    Column(modifier = Modifier.fillMaxSize().background(pageBg).padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.sub_page_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // 筛选行
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == SubscriptionFilter.ALL,
                onClick = { filter = SubscriptionFilter.ALL },
                label = { Text(stringResource(R.string.sub_filter_all)) },
            )
            FilterChip(
                selected = filter == SubscriptionFilter.ACTIVE,
                onClick = { filter = SubscriptionFilter.ACTIVE },
                label = { Text(stringResource(R.string.sub_filter_active)) },
            )
            FilterChip(
                selected = filter == SubscriptionFilter.INACTIVE,
                onClick = { filter = SubscriptionFilter.INACTIVE },
                label = { Text(stringResource(R.string.sub_filter_inactive)) },
            )
        }

        if (state.subscriptions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.sub_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
        ) {
            // 汇总卡
            item(key = "summary") {
                state.summary?.let { summary ->
                    SummaryCard(summary.monthly, summary.yearly, symbol)
                }
            }
            // 即将续费分区（有进行中的才显示）
            if (upcoming.isNotEmpty() && filter != SubscriptionFilter.INACTIVE) {
                item(key = "upcoming_header") {
                    Text(
                        stringResource(R.string.sub_upcoming_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(upcoming, key = { "up_${it.id}" }) { sub ->
                    SubscriptionItem(sub, onClick = { onOpenEdit(sub.id) })
                }
            }
            // 全部列表
            item(key = "list_header") {
                Text(
                    stringResource(
                        when (filter) {
                            SubscriptionFilter.ALL -> R.string.sub_list_title_all
                            SubscriptionFilter.ACTIVE -> R.string.sub_list_title_active
                            SubscriptionFilter.INACTIVE -> R.string.sub_list_title_inactive
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(filtered, key = { "sub_${it.id}" }) { sub ->
                SubscriptionItem(sub, onClick = { onOpenEdit(sub.id) })
            }
        }
    }
}

@Composable
private fun SummaryCard(monthly: Double, yearly: Double, symbol: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.sub_summary_monthly),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    MoneyFormat.format(monthly, symbol),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.sub_summary_yearly),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    MoneyFormat.format(yearly, symbol),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionItem(sub: Subscription, onClick: () -> Unit) {
    // 行内金额按订阅自身计费币种展示（不换汇）；只有汇总卡换算到默认展示币种。
    val symbol = stringResource(sub.currency.symbolRes)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (sub.active) 1f else 0.55f),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sub.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(sub.category.displayRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(
                    stringResource(R.string.sub_renewal_date, Subscription.dateOf(sub.nextRenewalEpochMs).toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                // 双价：月均 · 年化
                Text(
                    "${MoneyFormat.format(sub.monthlyAmount, symbol)} /${stringResource(R.string.sub_per_month)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${MoneyFormat.format(sub.yearlyAmount, symbol)} /${stringResource(R.string.sub_per_year)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
