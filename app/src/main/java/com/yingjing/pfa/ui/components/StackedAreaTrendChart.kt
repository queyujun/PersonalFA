package com.yingjing.pfa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yingjing.pfa.ui.theme.LocalBrandColors

/** 堆叠面积图的一条序列（一个资产类别）。[values] 对齐到共同时间轴，等长、非负。 */
data class StackedAreaSeries(val name: String, val color: Color, val values: List<Double>)

/**
 * 堆叠面积走势图（Canvas 自绘）。
 *
 * 各资产类别自下而上堆叠为彩色面积带，顶部包络线即总资产走势：
 * - 总资产规模与走向：看顶部包络线的高度与斜率。
 * - 各类资产占比与结构变化：看各色带的宽度及其随时间的消长。
 *
 * 仅需 [series] 各项 [StackedAreaSeries.values] 等长且至少 2 个时间点。
 * 堆叠顺序 = [series] 顺序（首项在底部）。颜色与饼图/走势详情保持一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StackedAreaTrendChart(
    series: List<StackedAreaSeries>,
    modifier: Modifier = Modifier,
    totalLineColor: Color = LocalBrandColors.current.primary,
) {
    if (series.isEmpty()) return
    val n = series.first().values.size
    if (n < 2) return

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val padTop = 10f
            val padBottom = 10f
            val chartHeight = size.height - padTop - padBottom
            val stepX = size.width / (n - 1)

            // 每个时间点 i 的堆叠总和（= 总资产），用于定顶部包络与纵向缩放。
            val totals = List(n) { i -> series.sumOf { it.values[i] } }
            val maxTotal = (totals.max()).takeIf { it > 0 } ?: 1.0

            fun yForValue(v: Double): Float =
                padTop + (1f - (v / maxTotal).toFloat()) * chartHeight

            // 逐类别自下而上画面积带：下边界=之前类别累计值，上边界=加上本类别。
            // 每轮新建累计数组，不 mutate 上一轮（不可变）。
            var accLower = FloatArray(n)
            series.forEach { s ->
                val upper = FloatArray(n) { i -> accLower[i] + s.values[i].toFloat() }

                val area = Path().apply {
                    // 上边界：左→右
                    moveTo(0f, yForValue(upper[0].toDouble()))
                    for (i in 1 until n) lineTo(i * stepX, yForValue(upper[i].toDouble()))
                    // 下边界：右→左（accLower）
                    for (i in n - 1 downTo 0) lineTo(i * stepX, yForValue(accLower[i].toDouble()))
                    close()
                }
                drawPath(area, color = s.color.copy(alpha = 0.82f))

                // 上边界细描边：相邻色带分层更清晰。
                val topEdge = Path().apply {
                    moveTo(0f, yForValue(upper[0].toDouble()))
                    for (i in 1 until n) lineTo(i * stepX, yForValue(upper[i].toDouble()))
                }
                drawPath(
                    topEdge,
                    color = s.color,
                    style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                accLower = upper
            }

            // 顶部总资产包络线 + 末点。
            val topLine = Path().apply {
                moveTo(0f, yForValue(totals[0]))
                for (i in 1 until n) lineTo(i * stepX, yForValue(totals[i]))
            }
            drawPath(
                topLine,
                color = totalLineColor,
                style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(
                totalLineColor,
                radius = 4f,
                center = Offset((n - 1) * stepX, yForValue(totals.last())),
            )
        }

        Spacer(Modifier.height(8.dp))
        // 紧凑图例：色块 + 类别名，自动换行。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) { drawRect(color = s.color) }
                    Spacer(Modifier.size(4.dp))
                    Text(s.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
