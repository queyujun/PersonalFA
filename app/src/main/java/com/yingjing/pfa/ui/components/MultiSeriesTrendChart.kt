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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.yingjing.pfa.domain.usecase.TrendChartData
import com.yingjing.pfa.ui.format.MoneyFormat

/**
 * 多序列走势图（Canvas 自绘）：Y 轴以「万」为单位；支持双指缩放 + 拖动平移。
 * 至少需要 2 个时间桶。
 */
@Composable
fun MultiSeriesTrendChart(
    data: TrendChartData,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(data) { scale = 1f; offsetX = 0f }

    val n = data.bucketLabels.size

    Canvas(
        modifier = modifier.pointerInput(data) {
            detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 12f)
                val plotW = size.width - LEFT_PAD - RIGHT_PAD
                val minOffset = (plotW - plotW * newScale).coerceAtMost(0f)
                scale = newScale
                offsetX = (offsetX + pan.x).coerceIn(minOffset, 0f)
            }
        },
    ) {
        if (n < 2) return@Canvas
        val plotW = size.width - LEFT_PAD - RIGHT_PAD
        val plotH = size.height - TOP_PAD - BOTTOM_PAD
        val contentW = plotW * scale
        val clampedOffset = offsetX.coerceIn((plotW - contentW).coerceAtMost(0f), 0f)
        val spacing = contentW / (n - 1)

        val allValues = data.series.flatMap { it.values.filterNotNull() }
        if (allValues.isEmpty()) return@Canvas
        var minV = allValues.min()
        var maxV = allValues.max()
        if (minV == maxV) { minV -= 1.0; maxV += 1.0 }
        val range = maxV - minV

        fun yOf(v: Double) = TOP_PAD + (1f - ((v - minV) / range).toFloat()) * plotH
        fun xOf(i: Int) = LEFT_PAD + clampedOffset + i * spacing

        // 横向网格 + Y 轴（万）标签
        val gridLines = 4
        for (g in 0..gridLines) {
            val v = minV + range * g / gridLines
            val y = yOf(v)
            drawLine(gridColor, Offset(LEFT_PAD, y), Offset(size.width - RIGHT_PAD, y), 1f)
            drawText(
                measurer,
                MoneyFormat.wan(v),
                topLeft = Offset(2f, y - 6.sp.toPx()),
                style = TextStyle(fontSize = 9.sp, color = labelColor),
            )
        }

        // 各序列折线
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

        // X 轴时间标签（稀疏）
        val step = (n / 5).coerceAtLeast(1)
        var i = 0
        while (i < n) {
            val x = xOf(i)
            if (x >= LEFT_PAD - 20f && x <= size.width - RIGHT_PAD) {
                drawText(
                    measurer,
                    data.bucketLabels[i],
                    topLeft = Offset(x - 16f, size.height - BOTTOM_PAD + 4f),
                    style = TextStyle(fontSize = 9.sp, color = labelColor),
                )
            }
            i += step
        }
    }
}

private const val LEFT_PAD = 56f
private const val RIGHT_PAD = 12f
private const val TOP_PAD = 12f
private const val BOTTOM_PAD = 28f
