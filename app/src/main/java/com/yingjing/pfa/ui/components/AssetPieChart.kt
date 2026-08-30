package com.yingjing.pfa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yingjing.pfa.R
import com.yingjing.pfa.ui.format.MoneyFormat
import kotlin.math.roundToInt

/** 饼图切片。 */
data class PieSlice(val label: String, val amount: Double, val color: Color)

/** 资产分布环形图（Canvas 自绘）+ 右侧图例（名称 + 金额万元 + 百分比，识别不依赖颜色）。 */
@Composable
fun AssetPieChart(slices: List<PieSlice>, currencySymbol: String, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.amount }
    if (total <= 0.0 || slices.isEmpty()) return

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(120.dp)) {
            Canvas(Modifier.size(120.dp)) {
                val stroke = size.minDimension * 0.20f
                val inset = stroke / 2f
                var startAngle = -90f
                slices.forEach { slice ->
                    val sweep = ((slice.amount / total) * 360.0).toFloat()
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                    startAngle += sweep
                }
            }
        }
        Column(
            modifier = Modifier
                .width(0.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            slices.forEach { slice ->
                val percent = ((slice.amount / total) * 100).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(9.dp)) { drawCircle(slice.color) }
                    Text(
                        "  ${slice.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MoneyText(
                        stringResource(R.string.pie_legend, currencySymbol, MoneyFormat.wan(slice.amount), percent),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
