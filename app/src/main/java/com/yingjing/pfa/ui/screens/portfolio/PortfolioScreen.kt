package com.yingjing.pfa.ui.screens.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.ui.components.MoneyText
import com.yingjing.pfa.ui.theme.Cat1
import com.yingjing.pfa.ui.theme.Cat2
import com.yingjing.pfa.ui.theme.Cat3
import com.yingjing.pfa.ui.theme.Cat4
import com.yingjing.pfa.ui.theme.Cat5
import com.yingjing.pfa.ui.theme.Cat6
import com.yingjing.pfa.ui.theme.Cat7
import com.yingjing.pfa.ui.theme.Cat8
import com.yingjing.pfa.ui.theme.Cat9
import com.yingjing.pfa.ui.theme.GainRed
import com.yingjing.pfa.ui.theme.LossGreen
import com.yingjing.pfa.ui.theme.LocalBrandColors

@Composable
fun PortfolioScreen(
    onOpenHolding: (Long) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val pageBg = LocalBrandColors.current.pageBackground

    if (state.isEmpty) {
        Box(
            modifier = Modifier.fillMaxSize().background(pageBg).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.portfolio_empty_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // 折叠状态（进程级，跨页面切换存活）：集合中存在=展开，不存在=折叠。
    // 初始空集 → 首次进入资产页全折叠；用户展开过的分类保留在 store，再次进入恢复上次状态。
    val expandedCategories by viewModel.expandedCategories.collectAsState()
    val expandedSubGroups by viewModel.expandedSubGroups.collectAsState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.sections.forEach { section ->
            item(key = "section_${section.category.name}") {
                val categoryKey = section.category.name
                CategoryBlock(
                    section = section,
                    expanded = categoryKey in expandedCategories,
                    onToggle = { viewModel.toggleCategory(categoryKey) },
                    expandedSubGroups = expandedSubGroups,
                    onToggleSub = { key -> viewModel.toggleSubGroup(key) },
                    onOpenHolding = onOpenHolding,
                )
            }
        }
    }
}

@Composable
private fun CategoryBlock(
    section: PortfolioSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedSubGroups: Set<String>,
    onToggleSub: (String) -> Unit,
    onOpenHolding: (Long) -> Unit,
) {
    val color = categoryColor(section.category)
    val collapseCd = stringResource(R.string.cd_collapse)
    val expandCd = stringResource(R.string.cd_expand)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 4.dp),
    ) {
        // 一级标题行：点击整行折叠/展开；右侧显示类目合计
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) collapseCd else expandCd,
                tint = color,
            )
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            MoneyText(
                section.totalText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (expanded) {
            section.subGroups.forEach { sg ->
                val title = sg.title
                if (title == null) {
                    // 非股票：无子标题，直接列条目
                    sg.rows.forEach { row -> HoldingRowItem(row = row, onClick = { onOpenHolding(row.id) }) }
                } else {
                    // 二级子类目：可各自折叠。key 用 locale 无关的 subKey，语言切换不丢折叠态。
                    val key = sg.subKey ?: "${section.category.name}|$title"
                    val sgExpanded = key in expandedSubGroups
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSub(key) }
                            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (sgExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (sgExpanded) collapseCd else expandCd,
                            tint = color,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            modifier = Modifier.weight(1f).padding(start = 2.dp),
                        )
                        MoneyText(
                            sg.totalText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (sgExpanded) {
                        sg.rows.forEach { row -> HoldingRowItem(row = row, onClick = { onOpenHolding(row.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldingRowItem(row: HoldingRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(row.valueText, style = MaterialTheme.typography.titleMedium)
                row.profitText?.let {
                    MoneyText(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.profitPositive) GainRed else LossGreen,
                    )
                }
            }
        }
    }
}

/** 类目背景/强调色，与走势图 seriesColor 同一套，保持全 App 一致。 */
private fun categoryColor(category: AssetCategory): Color = when (category) {
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
}
