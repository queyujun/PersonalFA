package com.yingjing.pfa.ui.screens.sentiment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yingjing.pfa.domain.model.SeriesPoint
import com.yingjing.pfa.domain.usecase.FearGreedZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** 走势图配色（由调用方按主题解析后传入，Canvas 内不读 CompositionLocal）。 */
data class FearChartColors(
    val primary: Color,
    val overlay: Color,
    val axisText: Color,
    val grid: Color,
    val band: Color,
    val median: Color,
    val crosshair: Color,
    val bubbleBg: Color,
    val bubbleText: Color,
    /** 恐惧贪婪五档背景（极度恐慌 → 极度贪婪）。 */
    val zoneFills: List<Color>,
)

/**
 * 恐慌指数双轴走势图（Canvas 自绘）：
 * - 左轴：恐慌指数（主线 + 区间 25–75 分位带 + 中位数虚线；恐惧贪婪改画固定五档背景，轴固定 0–100）。
 * - 右轴：叠加序列（相关价格 / VIX），独立刻度，便于观察「指数升、价格跌」的反向关系。
 * - X 轴：按 epochDay 线性映射（两序列交易日不完全一致时各自连线，不强行对齐）。
 * - 触摸 / 拖动：十字准线 + 气泡显示该日指数值与叠加值。
 */
@Composable
fun FearHistoryChart(
    data: FearChartData,
    isFearGreed: Boolean,
    showOverlay: Boolean,
    primaryDecimals: Int,
    overlayDecimals: Int,
    colors: FearChartColors,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var touchX by remember(data) { mutableStateOf<Float?>(null) }
    val axisStyle = TextStyle(fontSize = 10.sp, color = colors.axisText)
    val bubbleStyle = TextStyle(fontSize = 11.sp, color = colors.bubbleText, fontWeight = FontWeight.SemiBold)

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(data) {
                detectTapGestures(onPress = { pos ->
                    touchX = pos.x
                    tryAwaitRelease()
                    touchX = null
                })
            }
            .pointerInput(data) {
                // 只拦截水平拖动：竖向滑动交给外层 verticalScroll，避免手指落在图上时页面滚不动。
                detectHorizontalDragGestures(
                    onDragStart = { touchX = it.x },
                    onDragEnd = { touchX = null },
                    onDragCancel = { touchX = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        touchX = change.position.x
                    },
                )
            },
    ) {
        val overlayOn = showOverlay && data.overlay.size >= 2
        val leftScale = if (isFearGreed) AxisScale(0.0, 100.0, listOf(0.0, 25.0, 50.0, 75.0, 100.0))
        else niceScale(data.primary.minOf { it.value }, data.primary.maxOf { it.value })
        val rightScale = if (overlayOn) niceScale(data.overlay.minOf { it.value }, data.overlay.maxOf { it.value }) else null

        val leftW = maxLabelWidth(measurer, leftScale, primaryDecimals, axisStyle) + 6.dp.toPx()
        val rightW = rightScale?.let { maxLabelWidth(measurer, it, overlayDecimals, axisStyle) + 6.dp.toPx() } ?: 8.dp.toPx()
        val plot = PlotArea(
            left = leftW,
            top = 8.dp.toPx(),
            right = size.width - rightW,
            bottom = size.height - 18.dp.toPx(),
        )
        val span = (data.endDay - data.startDay).coerceAtLeast(1)
        fun xOf(day: Long) = plot.left + (day - data.startDay).toFloat() / span * plot.width
        fun yOf(v: Double, s: AxisScale) = plot.bottom - ((v - s.min) / (s.max - s.min)).toFloat() * plot.height

        // 背景：恐惧贪婪五档 / 波动率分位带
        if (isFearGreed) {
            FearGreedZone.entries.forEachIndexed { i, z ->
                val yTop = yOf((z.upper + if (z == FearGreedZone.EXTREME_GREED) 0.0 else 0.5), leftScale)
                val yBottom = yOf((z.lower - if (z == FearGreedZone.EXTREME_FEAR) 0.0 else 0.5), leftScale)
                drawRect(colors.zoneFills[i], Offset(plot.left, yTop), Size(plot.width, yBottom - yTop))
            }
        } else if (data.bandLow != null && data.bandHigh != null) {
            val yTop = yOf(data.bandHigh, leftScale)
            val yBottom = yOf(data.bandLow, leftScale)
            drawRect(colors.band, Offset(plot.left, yTop), Size(plot.width, yBottom - yTop))
        }

        // 网格 + 左轴刻度
        leftScale.ticks.forEach { t ->
            val y = yOf(t, leftScale)
            drawLine(colors.grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            drawLabel(measurer, FearFormat.value(t, if (isFearGreed) 0 else axisDecimals(leftScale)), axisStyle,
                x = plot.left - 4.dp.toPx(), y = y, alignRight = true)
        }
        // 右轴刻度（叠加序列）
        rightScale?.ticks?.forEach { t ->
            drawLabel(measurer, FearFormat.value(t, axisDecimals(rightScale)), axisStyle.copy(color = colors.overlay),
                x = plot.right + 4.dp.toPx(), y = yOf(t, rightScale), alignRight = false)
        }
        // 中位数虚线
        data.median?.let { m ->
            val y = yOf(m, leftScale)
            drawLine(colors.median, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        // X 轴日期（首 / 中 / 尾）
        listOf(data.startDay, data.startDay + span / 2, data.endDay).forEachIndexed { i, day ->
            val text = FearFormat.axisDay(day, span)
            val layout = measurer.measure(text, axisStyle)
            val x = when (i) {
                0 -> plot.left
                2 -> plot.right - layout.size.width
                else -> xOf(day) - layout.size.width / 2f
            }
            drawText(layout, topLeft = Offset(x, plot.bottom + 4.dp.toPx()))
        }

        // 叠加线（先画，主线压在上面）
        if (overlayOn && rightScale != null) {
            drawSeries(data.overlay, colors.overlay, 1.6.dp.toPx(), fill = false, plot) { xOf(it.epochDay) to yOf(it.value, rightScale) }
        }
        drawSeries(data.primary, colors.primary, 2.dp.toPx(), fill = true, plot) { xOf(it.epochDay) to yOf(it.value, leftScale) }
        // 末点
        data.primary.last().let { p -> drawCircle(colors.primary, 4.dp.toPx(), Offset(xOf(p.epochDay), yOf(p.value, leftScale))) }

        // 十字准线 + 气泡
        touchX?.let { tx ->
            val day = data.startDay + ((tx - plot.left) / plot.width * span).toLong().coerceIn(0, span)
            val p = nearest(data.primary, day) ?: return@let
            val x = xOf(p.epochDay)
            drawLine(colors.crosshair, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.5f)
            drawCircle(colors.primary, 5.dp.toPx(), Offset(x, yOf(p.value, leftScale)))
            val o = if (overlayOn && rightScale != null) nearest(data.overlay, p.epochDay, maxGap = 4) else null
            o?.let { drawCircle(colors.overlay, 4.dp.toPx(), Offset(xOf(it.epochDay), yOf(it.value, rightScale!!))) }
            val lines = buildList {
                add(FearFormat.day(p.epochDay))
                add("● " + FearFormat.value(p.value, primaryDecimals))
                o?.let { add("● " + FearFormat.value(it.value, overlayDecimals)) }
            }
            drawBubble(measurer, lines, bubbleStyle, colors, overlayOn, x, plot)
        }
    }
}

private data class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

internal data class AxisScale(val min: Double, val max: Double, val ticks: List<Double>)

/** 「好看」的轴刻度：4–5 个整齐刻度，min/max 外扩到刻度边界。 */
internal fun niceScale(rawMin: Double, rawMax: Double, targetTicks: Int = 4): AxisScale {
    var lo = rawMin
    var hi = rawMax
    if (hi - lo < 1e-9) {
        val pad = if (abs(lo) < 1e-9) 1.0 else abs(lo) * 0.05
        lo -= pad
        hi += pad
    }
    val rawStep = (hi - lo) / targetTicks
    val mag = 10.0.pow(floor(log10(rawStep)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= rawStep }
    val min = floor(lo / step) * step
    val max = ceil(hi / step) * step
    val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
    return AxisScale(min, max, ticks)
}

/** 刻度小数位：步长 < 1 时保留 1–2 位。 */
private fun axisDecimals(scale: AxisScale): Int {
    val step = if (scale.ticks.size >= 2) scale.ticks[1] - scale.ticks[0] else 1.0
    return when {
        step >= 1.0 -> 0
        step >= 0.1 -> 1
        else -> 2
    }
}

private fun maxLabelWidth(measurer: TextMeasurer, scale: AxisScale, decimals: Int, style: TextStyle): Float =
    scale.ticks.maxOf { measurer.measure(FearFormat.value(it, axisDecimals(scale).coerceAtMost(maxOf(decimals, 0))), style).size.width }.toFloat()

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float, alignRight: Boolean) {
    val layout: TextLayoutResult = measurer.measure(text, style)
    val left = if (alignRight) x - layout.size.width else x
    drawText(layout, topLeft = Offset(left, y - layout.size.height / 2f))
}

private fun DrawScope.drawSeries(
    points: List<SeriesPoint>,
    color: Color,
    width: Float,
    fill: Boolean,
    plot: PlotArea,
    map: (SeriesPoint) -> Pair<Float, Float>,
) {
    if (points.size < 2) return
    val offsets = points.map { map(it).let { (x, y) -> Offset(x, y) } }
    val line = Path().apply {
        moveTo(offsets.first().x, offsets.first().y)
        offsets.drop(1).forEach { lineTo(it.x, it.y) }
    }
    if (fill) {
        val area = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, plot.bottom)
            lineTo(offsets.first().x, plot.bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0f)), startY = plot.top, endY = plot.bottom))
    }
    drawPath(line, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 二分查找最接近 [day] 的点；[maxGap] 非 null 时距离超过即返回 null。 */
internal fun nearest(points: List<SeriesPoint>, day: Long, maxGap: Long? = null): SeriesPoint? {
    if (points.isEmpty()) return null
    var lo = 0
    var hi = points.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (points[mid].epochDay < day) lo = mid + 1 else hi = mid
    }
    val candidates = listOfNotNull(points.getOrNull(lo - 1), points.getOrNull(lo))
    val best = candidates.minByOrNull { abs(it.epochDay - day) } ?: return null
    return if (maxGap != null && abs(best.epochDay - day) > maxGap) null else best
}

private fun DrawScope.drawBubble(
    measurer: TextMeasurer,
    lines: List<String>,
    style: TextStyle,
    colors: FearChartColors,
    overlayOn: Boolean,
    anchorX: Float,
    plot: PlotArea,
) {
    val layouts = lines.mapIndexed { i, text ->
        val c = when {
            i == 1 -> colors.primary
            i == 2 && overlayOn -> colors.overlay
            else -> colors.bubbleText
        }
        measurer.measure(text, style.copy(color = if (i == 0) colors.bubbleText else c))
    }
    val pad = 6.dp.toPx()
    val w = layouts.maxOf { it.size.width } + pad * 2
    val h = layouts.sumOf { it.size.height } + pad * 2
    val left = if (anchorX + 10.dp.toPx() + w <= plot.right) anchorX + 10.dp.toPx() else anchorX - 10.dp.toPx() - w
    val top = plot.top + 2.dp.toPx()
    drawRoundRect(colors.bubbleBg, Offset(left, top), Size(w, h), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
    var y = top + pad
    layouts.forEach {
        drawText(it, topLeft = Offset(left + pad, y))
        y += it.size.height
    }
}
