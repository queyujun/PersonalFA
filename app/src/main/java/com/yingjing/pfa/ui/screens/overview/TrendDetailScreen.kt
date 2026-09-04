package com.yingjing.pfa.ui.screens.overview

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.usecase.TREND_CUSTOM_ID
import com.yingjing.pfa.domain.usecase.TREND_TOTAL_ID
import com.yingjing.pfa.domain.usecase.TimeGranularity
import com.yingjing.pfa.domain.usecase.TrendSeriesBuilder
import com.yingjing.pfa.domain.usecase.TrendSeries
import com.yingjing.pfa.ui.components.MultiSeriesTrendChart
import com.yingjing.pfa.ui.components.MoneyText
import com.yingjing.pfa.ui.format.MoneyFormat
import com.yingjing.pfa.ui.theme.Cat1
import com.yingjing.pfa.ui.theme.Cat2
import com.yingjing.pfa.ui.theme.Cat3
import com.yingjing.pfa.ui.theme.Cat4
import com.yingjing.pfa.ui.theme.Cat5
import com.yingjing.pfa.ui.theme.Cat6
import com.yingjing.pfa.ui.theme.Cat7
import com.yingjing.pfa.ui.theme.Cat8
import com.yingjing.pfa.ui.theme.Cat9
import com.yingjing.pfa.ui.theme.CustomCombo
import com.yingjing.pfa.ui.theme.GainRed
import com.yingjing.pfa.ui.theme.LocalBrandColors
import com.yingjing.pfa.ui.theme.LossGreen

/** 可参与自定义组合的资产类别（排除负债：负债为负值，混入合计会误导）。 */
private val COMBO_CATEGORIES: List<AssetCategory> = listOf(
    AssetCategory.REAL_ESTATE,
    AssetCategory.DEPOSIT,
    AssetCategory.STOCK,
    AssetCategory.GOLD,
    AssetCategory.BOND,
    AssetCategory.EQUITY,
    AssetCategory.CRYPTO,
    AssetCategory.OTC_FUND,
)

/** 时间范围快捷（null = 全部）。按自然日裁剪最新 N 天，三种粒度通用。 */
private enum class TrendRange(@StringRes val labelRes: Int, val days: Int?) {
    D7(R.string.trend_range_7d, 7),
    D30(R.string.trend_range_30d, 30),
    ALL(R.string.trend_range_all, null),
}

@Composable
fun TrendDetailScreen(
    onBack: () -> Unit,
    viewModel: TrendDetailViewModel = hiltViewModel(),
) {
    val raw by viewModel.raw.collectAsState()
    var granularity by remember { mutableStateOf(TimeGranularity.DAY) }
    var range by remember { mutableStateOf(TrendRange.ALL) }
    var selected by remember { mutableStateOf(setOf(TREND_TOTAL_ID)) }
    // 自定义组合选中的资产类别（仅 TREND_CUSTOM_ID 被勾选时使用）
    var customCategories by remember { mutableStateOf(setOf<String>()) }

    // 预解析资产类别名 → 本地化文案（纯函数 build() 与多个 chip 复用，避免在非 @Composable 处调 stringResource）。
    val categoryLabels: Map<String, String> = AssetCategory.entries.associate { it.name to stringResource(it.displayRes) }
    val totalLabel = stringResource(R.string.trend_total)
    val customLabel = stringResource(R.string.trend_custom)

    // 前两项固定（总净值/自定义组合）；其余资产类别按最新一天金额从大到小排序。
    val available = remember(raw) {
        val latestAmount = raw.categories.groupBy { it.category }
            .mapValues { (_, pts) -> pts.maxByOrNull { it.epochDay }?.amount ?: 0.0 }
        listOf(TREND_TOTAL_ID, TREND_CUSTOM_ID) +
            latestAmount.entries.sortedByDescending { it.value }.map { it.key }
    }
    val selectedIds = available.filter { it in selected }.ifEmpty { listOf(TREND_TOTAL_ID) }

    // 以数据中最晚一天为「今天」锚点（不依赖系统时钟，确定性、可测）。
    val todayEpoch = remember(raw) {
        val t = raw.totals.maxOfOrNull { it.epochDay }
        val c = raw.categories.maxOfOrNull { it.epochDay }
        maxOf(t ?: 0L, c ?: 0L)
    }
    val filteredTotals = remember(raw, todayEpoch, range) {
        val days = range.days ?: return@remember raw.totals
        val threshold = todayEpoch - (days - 1)
        raw.totals.filter { it.epochDay >= threshold }
    }
    val filteredCategories = remember(raw, todayEpoch, range) {
        val days = range.days ?: return@remember raw.categories
        val threshold = todayEpoch - (days - 1)
        raw.categories.filter { it.epochDay >= threshold }
    }

    val chartData = remember(filteredTotals, filteredCategories, selectedIds, granularity, customCategories, categoryLabels, totalLabel, customLabel) {
        TrendSeriesBuilder.build(
            totals = filteredTotals,
            categories = filteredCategories,
            selectedIds = selectedIds,
            granularity = granularity,
            categoryLabel = { categoryLabels[it] ?: it },
            totalLabel = totalLabel,
            customLabel = customLabel,
            customCategories = customCategories,
        )
    }
    val brandTotalColor = LocalBrandColors.current.primary
    val colors = selectedIds.map { seriesColor(it, brandTotalColor) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 横屏纵向空间紧张：收紧控制行/图例的纵向内边距、压低顶栏、隐藏底部提示，把高度让给走势图。
    val controlVerticalPad = if (isLandscape) 0.dp else 2.dp

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isLandscape) Modifier.height(44.dp) else Modifier.height(52.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.trend_detail_title), style = MaterialTheme.typography.titleLarge)
        }

        // 紧凑控制区：日/月/年 与 近7天/近30天/全部 合并为一行，节省纵向空间。
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = controlVerticalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TimeGranularity.entries.forEach { g ->
                FilterChip(
                    selected = granularity == g,
                    onClick = { granularity = g },
                    label = { Text(stringResource(g.labelRes), style = MaterialTheme.typography.labelMedium) },
                )
            }
            // 视觉分隔
            Box(
                Modifier
                    .height(18.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            TrendRange.entries.forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { range = r },
                    label = { Text(stringResource(r.labelRes), style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        // 资产类别多选（总净值 + 自定义 + 各类别）
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = controlVerticalPad),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            available.forEach { id ->
                FilterChip(
                    selected = id in selectedIds,
                    onClick = {
                        selected = if (id in selected) (selected - id) else (selected + id)
                        if (selected.none { it in available }) selected = setOf(TREND_TOTAL_ID)
                    },
                    label = { Text(seriesLabelWithTotal(id, totalLabel, customLabel, categoryLabels), style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        // 自定义组合的二级类别多选（仅当「自定义」被勾选时展开）
        AnimatedVisibility(visible = TREND_CUSTOM_ID in selectedIds) {
            CustomComboSelector(
                selectedCategories = customCategories,
                onToggle = { name ->
                    customCategories = if (name in customCategories) customCategories - name else customCategories + name
                },
                onReplace = { customCategories = it },
                categoryLabels = categoryLabels,
            )
        }

        if (chartData.bucketLabels.size < 2) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.trend_accumulating_long),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // 横屏时统计卡折叠到顶部一行；竖屏保持原卡片。
            if (!isLandscape) {
                TrendStatsCard(
                    stats = remember(chartData) { trendStats(chartData.series) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            MultiSeriesTrendChart(
                data = chartData,
                colors = colors,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(if (isLandscape) 4.dp else 8.dp),
            )
            // 可点击图例：点击切换该序列可见性
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = controlVerticalPad),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                chartData.series.forEachIndexed { i, s ->
                    val id = selectedIds.getOrElse(i) { TREND_TOTAL_ID }
                    LegendItem(
                        name = s.name,
                        color = colors.getOrElse(i) { Color.Gray },
                        onClick = {
                            selected = if (id in selected) (selected - id) else (selected + id)
                            if (selected.none { it in available }) selected = setOf(TREND_TOTAL_ID)
                        },
                    )
                }
            }
            // 横屏纵向空间紧张，隐藏底部提示以把高度让给走势图
            if (!isLandscape) {
                Text(
                    stringResource(R.string.trend_gesture_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 区间统计：当前值、盈亏金额与百分比、高低点（均以「万」为单位展示）。 */
private data class TrendStats(
    val current: Double,
    val delta: Double,
    val deltaPercent: Double,
    val high: Double,
    val low: Double,
    val hasData: Boolean,
)

/** 取展示主序列：优先总净值，否则首条可见序列。统计其非空值的区间表现。 */
private fun trendStats(series: List<TrendSeries>): TrendStats {
    val primary = series.firstOrNull { it.id == TREND_TOTAL_ID } ?: series.firstOrNull()
    val values = primary?.values?.filterNotNull().orEmpty()
    if (values.isEmpty()) return TrendStats(0.0, 0.0, 0.0, 0.0, 0.0, hasData = false)
    val first = values.first()
    val last = values.last()
    val delta = last - first
    val pct = if (first != 0.0) delta / first * 100.0 else 0.0
    return TrendStats(
        current = last,
        delta = delta,
        deltaPercent = pct,
        high = values.max(),
        low = values.min(),
        hasData = true,
    )
}

@Composable
private fun TrendStatsCard(
    stats: TrendStats,
    modifier: Modifier = Modifier,
) {
    if (!stats.hasData) return
    val gainColor = if (stats.delta >= 0) GainRed else LossGreen
    val sign = if (stats.delta > 0) "+" else if (stats.delta < 0) "" else ""
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.trend_current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(
                    stringResource(R.string.trend_wan_unit, MoneyFormat.wan(stats.current)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.trend_range_pnl), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    MoneyText(
                        stringResource(R.string.trend_wan_unit, "$sign${MoneyFormat.wan(stats.delta)}"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = gainColor,
                    )
                    Spacer(Modifier.width(6.dp))
                    MoneyText(
                        "${"%.1f".format(stats.deltaPercent)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = gainColor,
                    )
                }
                MoneyText(
                    stringResource(R.string.trend_high_low, MoneyFormat.wan(stats.high), MoneyFormat.wan(stats.low)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 可点击图例项：色点 + 名称；点击切换对应序列可见性。 */
@Composable
private fun LegendItem(
    name: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 自定义组合的资产类别多选器：一行 chip 展示已选类别，加一个下拉「+ 类别」追加。
 * 排除负债（负值混入合计会误导）。支持「全选 / 清空」快捷（不可变整集合替换）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomComboSelector(
    selectedCategories: Set<String>,
    onToggle: (String) -> Unit,
    onReplace: (Set<String>) -> Unit,
    categoryLabels: Map<String, String>,
) {
    val availableNames = COMBO_CATEGORIES.map { it.name }
    val unselected = availableNames.filter { it !in selectedCategories }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            stringResource(R.string.trend_custom_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 快捷：全选 / 清空（不可变整集合替换）
            TextButton(
                onClick = { onReplace(availableNames.toSet()) },
                enabled = unselected.isNotEmpty(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.common_select_all), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = { onReplace(emptySet()) },
                enabled = selectedCategories.isNotEmpty(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.common_clear), style = MaterialTheme.typography.labelMedium)
            }

            // 已选类别 chip（可点取消）
            selectedCategories.forEach { name ->
                AssistChip(
                    onClick = { onToggle(name) },
                    label = { Text(seriesLabel(name, categoryLabels), style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = CustomCombo.copy(alpha = 0.12f)),
                )
            }

            // 「+ 类别」下拉追加
            Box {
                AssistChip(
                    onClick = { menuExpanded = true },
                    enabled = unselected.isNotEmpty(),
                    label = { Text(stringResource(R.string.trend_add_category), style = MaterialTheme.typography.labelMedium) },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (unselected.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.trend_all_selected)) },
                            onClick = { menuExpanded = false },
                        )
                    } else {
                        unselected.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(seriesLabel(name, categoryLabels)) },
                                onClick = {
                                    onToggle(name)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun seriesLabel(categoryName: String, categoryLabels: Map<String, String>): String =
    categoryLabels[categoryName] ?: categoryName

private fun seriesLabelWithTotal(
    id: String,
    totalLabel: String,
    customLabel: String,
    categoryLabels: Map<String, String>,
): String = when (id) {
    TREND_TOTAL_ID -> totalLabel
    TREND_CUSTOM_ID -> customLabel
    else -> seriesLabel(id, categoryLabels)
}

private fun seriesColor(id: String, totalColor: Color): Color {
    if (id == TREND_TOTAL_ID) return totalColor
    if (id == TREND_CUSTOM_ID) return CustomCombo
    return when (runCatching { AssetCategory.valueOf(id) }.getOrNull()) {
        AssetCategory.REAL_ESTATE -> Cat1
        AssetCategory.DEPOSIT -> Cat2
        AssetCategory.STOCK -> Cat3
        AssetCategory.GOLD -> Cat4
        AssetCategory.BOND -> Cat5
        AssetCategory.CRYPTO -> Cat6
        AssetCategory.EQUITY -> Cat7
        AssetCategory.OTC_FUND -> Cat8
        AssetCategory.MISC -> Cat9
        AssetCategory.LIABILITY -> GainRed
        null -> Color.Gray
    }
}
