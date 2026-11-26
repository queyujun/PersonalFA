package com.yingjing.pfa.ui.screens.sentiment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.FearGroup
import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.FearScale
import com.yingjing.pfa.domain.model.PriceSeriesId
import com.yingjing.pfa.domain.repository.SentimentRefreshOutcome
import com.yingjing.pfa.domain.repository.SentimentRefreshResult
import com.yingjing.pfa.domain.usecase.FearComposite
import com.yingjing.pfa.ui.components.MoneyText
import com.yingjing.pfa.ui.theme.LocalBrandColors
import com.yingjing.pfa.ui.theme.LocalSemanticColors

/** 恐慌指数总览页：综合温度 + 分组卡片（股市波动率 / 加密情绪 / 商品波动率）。 */
@Composable
fun FearIndexScreen(
    onBack: () -> Unit,
    onOpenDetail: (FearIndexId) -> Unit,
    viewModel: FearIndexViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshIfStale() }
    LaunchedEffect(viewModel) {
        viewModel.refreshEvents.collect { snackbar.showSnackbar(refreshMessage(context, it)) }
    }

    Box(Modifier.fillMaxSize().background(LocalBrandColors.current.pageBackground)) {
        Column(Modifier.fillMaxSize()) {
            FearTopBar(
                title = stringResource(R.string.fear_title),
                refreshing = state.refreshing,
                onBack = onBack,
                onRefresh = viewModel::refreshNow,
            )
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                !state.loaded || (state.isEmpty && state.refreshing) -> CenterMessage(stringResource(R.string.fear_loading_first), loading = true)
                state.isEmpty -> EmptyState(onRetry = viewModel::refreshNow)
                else -> FearOverviewContent(state, onOpenDetail)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FearOverviewContent(state: FearOverviewUi, onOpenDetail: (FearIndexId) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.fear_header_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.asOfDay?.let {
                Text(stringResource(R.string.fear_updated_at, FearFormat.day(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.composite?.let { CompositeHero(it) }
        FearGroup.entries.forEach { group ->
            val cards = state.cards.filter { it.summary.id.group == group }
            if (cards.isEmpty()) return@forEach
            Text(
                stringResource(group.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            cards.forEach { card ->
                FearIndexCard(card, onClick = { onOpenDetail(card.summary.id) })
                Spacer(Modifier.height(10.dp))
            }
        }
        Text(
            stringResource(R.string.fear_source_cboe) + "\n" + stringResource(R.string.fear_source_fng),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
    }
}

/** 综合恐慌温度 hero 卡（品牌渐变）。 */
@Composable
private fun CompositeHero(composite: FearComposite) {
    val brand = LocalBrandColors.current
    val semantic = LocalSemanticColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(brand.gradientStart, brand.gradientEnd)), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fear_composite_title), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            LevelPill(stringResource(composite.level.compositeLabelRes()), Color.White, onDark = true)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            MoneyText(
                FearFormat.pct(composite.score),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(" / 100", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        PercentileTrack(composite.score, semantic, knobColor = Color.White, surface = brand.gradientStart)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(R.string.fear_scale_calm, R.string.fear_scale_neutral, R.string.fear_scale_fear).forEach {
                Text(stringResource(it), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.fear_composite_caption) + "\n" + stringResource(R.string.fear_composite_samples, composite.used, composite.total),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 单指数卡：名称 / 当前值 / 日变动 / 近 1 年分位轨道 / 迷你走势 / 区间。 */
@Composable
private fun FearIndexCard(card: FearCardUi, onClick: () -> Unit) {
    val s = card.summary
    val id = s.id
    val semantic = LocalSemanticColors.current
    val levelColor = s.level.color(semantic)
    val name = stringResource(id.nameRes)
    val isFng = id.scale == FearScale.FEAR_GREED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.cd_fear_open_detail, name)) { onClick() },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(id.subtitleRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        MoneyText(FearFormat.value(s.latest.value, id.decimals), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (isFng) Text("/100", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    ChangeText(s.change, s.changePct, id.decimals)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (isFng) {
                FearGreedGauge(s.latest.value, semantic)
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val pctText = stringResource(R.string.fear_pct_1y, FearFormat.pct(s.oneYear.percentile))
                Text(
                    if (isFng) pctText + " · " + stringResource(R.string.fear_pct_fng_hint) else pctText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.stale) {
                        LevelPill(stringResource(R.string.fear_stale_badge), MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                    }
                    LevelPill(stringResource(s.zone?.labelRes ?: s.level.labelRes), levelColor)
                }
            }
            if (!isFng) {
                Spacer(Modifier.height(6.dp))
                PercentileTrack(s.oneYear.percentile, semantic)
            }
            Sparkline(
                card.spark,
                color = if (isFng) semantic.fearHigh else LocalBrandColors.current.primary,
                modifier = Modifier.fillMaxWidth().height(36.dp).padding(top = 8.dp),
            )
            Text(
                stringResource(
                    R.string.fear_range_summary,
                    FearFormat.value(s.oneYear.min, id.decimals),
                    FearFormat.value(s.oneYear.max, id.decimals),
                    FearFormat.value(s.oneYear.mean, if (isFng) 1 else id.decimals),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 日变动：指数升 = 恐慌加剧（恐慌色），降 = 缓和（平静色）；恐惧贪婪方向相反由调用方语义决定，此处只按数值方向着色中性化。 */
@Composable
internal fun ChangeText(change: Double?, changePct: Double?, decimals: Int) {
    if (change == null) return
    val arrow = when {
        change > 0 -> "▲ "
        change < 0 -> "▼ "
        else -> ""
    }
    val pct = changePct?.let { " (" + FearFormat.signedPct(it) + ")" }.orEmpty()
    MoneyText(
        arrow + FearFormat.signed(change, decimals) + pct,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun FearTopBar(title: String, refreshing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_fear_refresh))
        }
    }
}

@Composable
internal fun CenterMessage(text: String, loading: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun EmptyState(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.fear_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.fear_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.fear_retry)) }
    }
}

/** 刷新结果 → Snackbar 文案（失败源转为本地化名称）。 */
internal fun refreshMessage(context: android.content.Context, result: SentimentRefreshResult): String = when (result.outcome) {
    SentimentRefreshOutcome.REFRESHED -> context.getString(R.string.fear_refresh_done)
    SentimentRefreshOutcome.SKIPPED -> context.getString(R.string.fear_refresh_skipped)
    SentimentRefreshOutcome.FAILED -> context.getString(R.string.fear_refresh_failed)
    SentimentRefreshOutcome.PARTIAL -> context.getString(
        R.string.fear_refresh_partial,
        result.failedCodes.mapNotNull { code -> seriesLabelRes(code)?.let(context::getString) }.joinToString("、"),
    )
}

private fun seriesLabelRes(code: String): Int? =
    FearIndexId.fromCode(code)?.nameRes ?: PriceSeriesId.entries.firstOrNull { it.code == code }?.labelRes
