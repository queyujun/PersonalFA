package com.yingjing.pfa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yingjing.pfa.domain.usecase.TrendChartData
import com.yingjing.pfa.ui.format.MoneyFormat

/**
 * 多序列走势图（Canvas 自绘）：Y 轴以「万」为单位；支持双指缩放 + 拖动平移。
 * 至少需要 2 个时间桶。
 *
 * 绘制要点：
 * - 边距用 dp/sp（密度、字体缩放无关）；底部留白按实测标签高度动态计算，避免横轴日期被裁。
 * - 横/纵网格均为虚线；X 轴日期标签水平居中并 clamp，防止首尾标签越界。
 * - 折线裁剪到绘图区，缩放/平移时不溢出到坐标轴与标签上。
 */
@Composable
fun MultiSeriesTrendChart(
    data: TrendChartData,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisLabelStyle = remember(labelColor) { TextStyle(fontSize = 9.sp, color = labelColor) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(data) { scale = 1f; offsetX = 0f }

    val n = data.bucketLabels.size

    Canvas(
        modifier = modifier.pointerInput(data) {
            detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 12f)
                val plotW = size.width - LEFT_PAD.toPx() - RIGHT_PAD.toPx()
                val minOffset = (plotW - plotW * newScale).coerceAtMost(0f)
                scale = newScale
                offsetX = (offsetX + pan.x).coerceIn(minOffset, 0f)
            }
        },
    ) {
        if (n < 2) return@Canvas
        val leftPad = LEFT_PAD.toPx()
        val rightPad = RIGHT_PAD.toPx()
        val topPad = TOP_PAD.toPx()
        val gap = AXIS_GAP.toPx()
        // 底部留白 = 实测标签高度 + 上下各一个间距；随字体缩放自适应，避免日期被 Canvas 边界裁切。
        val labelH = measurer.measure("0", axisLabelStyle).size.height.toFloat()
        val bottomPad = labelH + gap * 2f

        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        if (plotW <= 0f || plotH <= 0f) return@Canvas
        val contentW = plotW * scale
        val clampedOffset = offsetX.coerceIn((plotW - contentW).coerceAtMost(0f), 0f)
        val spacing = contentW / (n - 1)
        val plotBottom = topPad + plotH

        val allValues = data.series.flatMap { it.values.filterNotNull() }
        if (allValues.isEmpty()) return@Canvas
        var minV = allValues.min()
        var maxV = allValues.max()
        if (minV == maxV) { minV -= 1.0; maxV += 1.0 }
        val range = maxV - minV

        fun yOf(v: Double) = topPad + (1f - ((v - minV) / range).toFloat()) * plotH
        fun xOf(i: Int) = leftPad + clampedOffset + i * spacing

        val dash = PathEffect.dashPathEffect(floatArrayOf(gap, gap), 0f)

        // 横向网格（虚线）+ Y 轴（万）标签，标签垂直居中于网格线
        val gridLines = 4
        for (g in 0..gridLines) {
            val v = minV + range * g / gridLines
            val y = yOf(v)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 1f, pathEffect = dash)
            val ly = (y - labelH / 2f).coerceIn(0f, size.height - labelH)
            drawText(measurer, MoneyFormat.wan(v), topLeft = Offset(2f, ly), style = axisLabelStyle)
        }

        // 纵向网格（虚线）+ X 轴时间标签（稀疏、居中、不越界）
        val labelStep = (n / 5).coerceAtLeast(1)
        val xIndices = (0 until n step labelStep).toMutableList()
        if (n - 1 - xIndices.last() >= (labelStep + 1) / 2) xIndices.add(n - 1)
        xIndices.forEach { i ->
            val x = xOf(i)
            if (x < leftPad - 1f || x > size.width - rightPad + 1f) return@forEach
            drawLine(gridColor, Offset(x, topPad), Offset(x, plotBottom), 1f, pathEffect = dash)
            val layout = measurer.measure(data.bucketLabels[i], axisLabelStyle)
            val tx = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(tx, plotBottom + gap))
        }

        // 各序列折线（裁剪到绘图区，避免缩放/平移时溢出到坐标轴与标签）
        clipRect(left = leftPad, top = topPad, right = size.width - rightPad, bottom = plotBottom) {
            data.series.forEachIndexed { seriesIndex, series ->
                val color = colors.getOrElse(seriesIndex) { Color.Gray }
                var prev: Offset? = null
                series.values.forEachIndexed { i, value ->
                    if (value == null) { prev = null; return@forEachIndexed }
                    val point = Offset(xOf(i), yOf(value))
                    prev?.let { drawLine(color, it, point, 3f, cap = StrokeCap.Round) }
                    prev = point
                }
            }
        }
    }
}

private val LEFT_PAD = 44.dp
private val RIGHT_PAD = 12.dp
private val TOP_PAD = 12.dp
private val AXIS_GAP = 6.dp
