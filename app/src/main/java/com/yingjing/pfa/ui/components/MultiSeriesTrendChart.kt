package com.yingjing.pfa.ui.components

import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.values.ChartValuesProvider
import com.patrykandpatrick.vico.core.component.Component
import com.patrykandpatrick.vico.core.component.marker.MarkerComponent
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.text.HorizontalPosition
import com.patrykandpatrick.vico.core.component.text.TextComponent
import com.patrykandpatrick.vico.core.component.text.VerticalPosition
import com.patrykandpatrick.vico.core.context.DrawContext
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.marker.Marker
import com.patrykandpatrick.vico.core.marker.MarkerLabelFormatter
import com.yingjing.pfa.domain.usecase.TrendChartData
import com.yingjing.pfa.ui.format.MoneyFormat

/**
 * 十字准线 marker：在 Vico 默认的垂直基准线 + 顶部数值气泡基础上，额外绘制
 * - 一条水平基准线（跨 [bounds] 左右，位于触摸点 Y 处），
 * - X 轴时间标签（底部，取自 [bucketLabels]），
 * - Y 轴净值标签（左侧，[MoneyFormat.wan] 格式化）。
 *
 * 多序列时，水平基准线与 Y 标签以「主序列」为基准（[markedEntries] 的首个条目，
 * 即当前可见的优先序列），避免多线交叠时基准线频繁跳动。
 */
private class CrosshairMarker(
    label: TextComponent,
    indicator: Component?,
    guideline: LineComponent?,
    private val horizontalGuideline: LineComponent,
    private val axisLabel: TextComponent,
    private val bucketLabels: List<String>,
) : MarkerComponent(label = label, indicator = indicator, guideline = guideline) {

    override fun draw(
        context: DrawContext,
        bounds: RectF,
        markedEntries: List<Marker.EntryModel>,
        chartValuesProvider: ChartValuesProvider,
    ) {
        super.draw(context, bounds, markedEntries, chartValuesProvider)
        if (markedEntries.isEmpty()) return

        val first = markedEntries.first()
        val touchX = first.location.x
        val touchY = first.location.y

        // 水平基准线：跨图表绘制区域左右，位于触摸点 Y。
        horizontalGuideline.drawHorizontal(
            context = context,
            left = bounds.left,
            right = bounds.right,
            centerY = touchY,
        )

        val idx = first.entry.x.toInt().coerceIn(0, bucketLabels.lastIndex)
        val xText = bucketLabels[idx]
        val yText = MoneyFormat.wan(first.entry.y.toDouble())

        // X 轴时间标签：底部，水平居中于触摸点。
        axisLabel.drawText(
            context = context,
            text = xText,
            textX = touchX,
            textY = bounds.bottom,
            horizontalPosition = HorizontalPosition.Center,
            verticalPosition = VerticalPosition.Bottom,
        )
        // Y 轴净值标签：左侧，垂直居中于触摸点。
        axisLabel.drawText(
            context = context,
            text = yText,
            textX = bounds.left,
            textY = touchY,
            horizontalPosition = HorizontalPosition.Start,
            verticalPosition = VerticalPosition.Center,
        )
    }
}

/**
 * 多序列走势图（Vico 1.12.0 实现）：
 * - 三次贝塞尔平滑曲线 + 线下垂直渐变填充（lineSpec 默认即提供，alpha 0.5→0）。
 * - 触摸十字准线：垂直 + 水平 guideline，并在 X 轴（底部时间）与 Y 轴（左侧净值）标注当前坐标；
 *   顶部数值气泡列出各序列在该点的值（[CrosshairMarker]）。
 * - Y 轴以「万」为单位；X 轴显示时间桶标签，稀疏排布避免重叠。
 * - [TrendSeries.values] 中的 null 表示该桶无数据 → 该 x 不生成 entry，线条在该处断开。
 *
 * 至少需要 2 个时间桶；全空数据时显示占位文案。
 */
@Composable
fun MultiSeriesTrendChart(
    data: TrendChartData,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val bucketLabels = data.bucketLabels
    val hasData = remember(data) {
        data.series.any { series -> series.values.any { it != null } }
    }

    if (!hasData || bucketLabels.size < 2) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "数据积累中：每日自动记录一条，多用几天后即可查看走势。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // 各序列 → entry 列表（null 桶跳过，使该序列在该 x 处断开）。
    val producer = remember(data) {
        val entryLists: List<List<ChartEntry>> = data.series.map { series ->
            series.values.mapIndexedNotNull { i, value ->
                if (value == null) null else entryOf(i.toFloat(), value.toFloat())
            }
        }
        ChartEntryModelProducer(entryLists)
    }

    val lineSpecs = remember(colors) { colors.map { lineSpec(lineColor = it) } }
    val chart = lineChart(lines = lineSpecs)

    val labelStep = (bucketLabels.size / 6).coerceAtLeast(1)
    val startAxis = rememberStartAxis(
        valueFormatter = AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
            MoneyFormat.wan(value.toDouble())
        },
        itemPlacer = remember { AxisItemPlacer.Vertical.default(maxItemCount = 5) },
    )
    val bottomAxis = rememberBottomAxis(
        valueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            bucketLabels.getOrElse(value.toInt()) { "" }
        },
        itemPlacer = remember(labelStep) { AxisItemPlacer.Horizontal.default(spacing = labelStep) },
    )

    val guidelineColor = MaterialTheme.colorScheme.outline
    val label = textComponent(
        color = Color.White,
        textSize = 11.sp,
        background = shapeComponent(
            shape = Shapes.roundedCornerShape(allPercent = 25),
            color = Color.Black.copy(alpha = 0.72f),
        ),
    )
    val indicator = shapeComponent(shape = Shapes.pillShape, color = Color.White)
    val guideline = lineComponent(color = guidelineColor, thickness = 1.dp)
    // 水平准线（略淡于垂直准线，避免抢眼）。
    val horizontalGuideline = lineComponent(color = guidelineColor.copy(alpha = 0.5f), thickness = 1.dp)
    // X/Y 轴坐标标签：圆角黑底白字。
    val axisLabel = textComponent(
        color = Color.White,
        textSize = 10.sp,
        background = shapeComponent(
            shape = Shapes.roundedCornerShape(allPercent = 20),
            color = Color.Black.copy(alpha = 0.72f),
        ),
    )
    val marker = remember(label, indicator, guideline, horizontalGuideline, axisLabel, bucketLabels) {
        CrosshairMarker(
            label = label,
            indicator = indicator,
            guideline = guideline,
            horizontalGuideline = horizontalGuideline,
            axisLabel = axisLabel,
            bucketLabels = bucketLabels,
        ).apply {
            labelFormatter = MarkerLabelFormatter { markedEntries, _ ->
                if (markedEntries.isEmpty()) {
                    ""
                } else {
                    val idx = markedEntries.first().entry.x.toInt()
                        .coerceIn(0, bucketLabels.lastIndex)
                    buildString {
                        append(bucketLabels[idx])
                        markedEntries.forEach { model ->
                            append("\n")
                            append(MoneyFormat.wan(model.entry.y.toDouble()))
                        }
                    }
                }
            }
            onApplyEntryColor = { color -> (indicator as? ShapeComponent)?.color = color }
            indicatorSizeDp = 10f
        }
    }

    ProvideChartStyle(chartStyle = m3ChartStyle()) {
        Chart(
            chart = chart,
            chartModelProducer = producer,
            modifier = modifier,
            startAxis = startAxis,
            bottomAxis = bottomAxis,
            marker = marker,
            chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
            isZoomEnabled = true,
        )
    }
}
