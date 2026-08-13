package com.yingjing.pfa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/** 单序列净值走势线图（Canvas 自绘：区域渐变 + 折线 + 末点）。至少 2 个点。 */
@androidx.compose.runtime.Composable
fun NetWorthTrendChart(
    points: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return
    Canvas(modifier) {
        val maxV = points.max()
        val minV = points.min()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val padTop = 10f
        val padBottom = 10f
        val chartHeight = size.height - padTop - padBottom
        val stepX = size.width / (points.size - 1)

        val offsets = points.mapIndexed { i, v ->
            val x = i * stepX
            val y = padTop + (1f - ((v - minV) / range).toFloat()) * chartHeight
            Offset(x, y)
        }

        val baseline = size.height - padBottom
        val area = Path().apply {
            moveTo(offsets.first().x, baseline)
            offsets.forEach { lineTo(it.x, it.y) }
            lineTo(offsets.last().x, baseline)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)),
            ),
        )

        val line = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            line,
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(lineColor, radius = 5f, center = offsets.last())
    }
}
