package com.yingjing.pfa.ui.screens.sentiment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.FearScale
import com.yingjing.pfa.ui.components.MoneyText
import com.yingjing.pfa.ui.theme.LocalBrandColors
import com.yingjing.pfa.ui.theme.LocalSemanticColors

/** 总览卡片上展示的三个关键指数（其余在恐慌指数页查看）。 */
private val ENTRY_INDICES = listOf(FearIndexId.VIX, FearIndexId.VXN, FearIndexId.CRYPTO_FNG)

/**
 * 总览页「恐慌指数」入口卡：综合温度 + 温度条 + 三个关键指数迷你走势，整卡点击进入恐慌指数页。
 * 只读缓存；首次进入总览时按新鲜度窗口后台补一次数据（失败静默，不打扰总览）。
 */
@Composable
fun FearEntryCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FearIndexViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val semantic = LocalSemanticColors.current
    val brand = LocalBrandColors.current
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    Card(
        modifier = modifier.fillMaxWidth().clickable { onOpen() },
        border = BorderStroke(1.dp, brand.primary.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fear_entry_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.fear_entry_more), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            val composite = state.composite
            if (composite == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.refreshing) stringResource(R.string.fear_loading_first) else stringResource(R.string.fear_entry_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(FearFormat.pct(composite.score), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("/100", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    LevelPill(stringResource(composite.level.compositeLabelRes()), composite.level.color(semantic))
                    Text(
                        stringResource(R.string.fear_composite_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PercentileTrack(composite.score, semantic)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(R.string.fear_scale_calm, R.string.fear_scale_neutral, R.string.fear_scale_fear).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ENTRY_INDICES.forEach { id ->
                    val card = state.cards.firstOrNull { it.summary.id == id }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(id.nameRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (card == null) {
                            Text("—", style = MaterialTheme.typography.titleMedium)
                            return@Column
                        }
                        val s = card.summary
                        MoneyText(FearFormat.value(s.latest.value, id.decimals), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (id.scale == FearScale.FEAR_GREED) stringResource(s.zone?.labelRes ?: s.level.labelRes)
                            else stringResource(R.string.fear_pct_1y_short, FearFormat.pct(s.oneYear.percentile)),
                            style = MaterialTheme.typography.labelSmall,
                            color = s.level.color(semantic),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Sparkline(
                            card.spark,
                            color = if (id.scale == FearScale.FEAR_GREED) semantic.fearHigh else brand.primary,
                            modifier = Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.asOfDay?.let {
                    Text(stringResource(R.string.fear_updated_at, FearFormat.day(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.fear_entry_all, FearIndexId.entries.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
