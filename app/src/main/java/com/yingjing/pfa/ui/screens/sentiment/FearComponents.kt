package com.yingjing.pfa.ui.screens.sentiment

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.usecase.FearGreedZone
import com.yingjing.pfa.domain.usecase.FearLevel
import com.yingjing.pfa.ui.theme.SemanticColors
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 恐慌程度 → 语义色（与红涨绿跌独立：红 = 恐慌，绿 = 平静）。 */
internal fun FearLevel.color(semantic: SemanticColors): Color = when (this) {
    FearLevel.VERY_LOW -> semantic.fearVeryLow
    FearLevel.LOW -> semantic.fearLow
    FearLevel.NEUTRAL -> semantic.fearNeutral
    FearLevel.HIGH -> semantic.fearHigh
    FearLevel.VERY_HIGH -> semantic.fearVeryHigh
}

/** 综合温度档文案（与单指数「偏高/极高」用词不同：偏恐慌 / 极度恐慌）。 */
@StringRes
internal fun FearLevel.compositeLabelRes(): Int = when (this) {
    FearLevel.VERY_LOW -> R.string.fear_composite_very_low
    FearLevel.LOW -> R.string.fear_composite_low
    FearLevel.NEUTRAL -> R.string.fear_composite_neutral
    FearLevel.HIGH -> R.string.fear_composite_high
    FearLevel.VERY_HIGH -> R.string.fear_composite_very_high
}

/** 恐慌温度渐变（平静 → 恐慌）。 */
internal fun fearGradient(semantic: SemanticColors): List<Color> = listOf(
    semantic.fearVeryLow, semantic.fearLow, semantic.fearNeutral, semantic.fearHigh, semantic.fearVeryHigh,
)

internal object FearFormat {
    private fun pattern(decimals: Int): DecimalFormat =
        DecimalFormat(if (decimals <= 0) "#,##0" else "#,##0." + "0".repeat(decimals))

    fun value(v: Double, decimals: Int): String = pattern(decimals).format(v)

    fun signed(v: Double, decimals: Int): String = (if (v > 0) "+" else if (v < 0) "−" else "") + value(kotlin.math.abs(v), decimals)

    fun signedPct(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + DecimalFormat("0.00").format(kotlin.math.abs(v)) + "%"

    /** 分位整数文本（四舍五入，0–100）。 */
    fun pct(v: Double): String = v.coerceIn(0.0, 100.0).let { Math.round(it).toString() }

    fun corr(v: Double): String = (if (v < 0) "−" else "") + DecimalFormat("0.00").format(kotlin.math.abs(v))

    /** 日期（与应用其他页面一致的 yyyy-MM-dd，不随系统 locale 变化）。 */
    fun day(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DAY_FORMAT)

    private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 坐标轴短日期：区间 ≤ 400 天用 月/日，否则 年/月。 */
    fun axisDay(epochDay: Long, spanDays: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return if (spanDays <= 400) "${d.monthValue}/${d.dayOfMonth}" else "${d.year % 100}/${d.monthValue.toString().padStart(2, '0')}"
    }
}

/** 等级徽章（圆角浅底 + 同色字）。 */
@Composable
internal fun LevelPill(text: String, color: Color, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val bg = if (onDark) Color.White.copy(alpha = 0.18f) else color.copy(alpha = 0.14f)
    val fg = if (onDark) Color.White else color
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * 分位轨道：渐变条 + 圆形游标。[percent] 0–100；[reversed] 为 true 时渐变方向反转
 * （恐惧贪婪：低值 = 恐慌在左侧红色）。
 */
@Composable
internal fun PercentileTrack(
    percent: Double,
    semantic: SemanticColors,
    modifier: Modifier = Modifier,
    reversed: Boolean = false,
    knobColor: Color = MaterialTheme.colorScheme.onSurface,
    surface: Color = MaterialTheme.colorScheme.surface,
) {
    val colors = fearGradient(semantic).let { if (reversed) it.reversed() else it }
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val trackH = 7.dp.toPx()
        val top = (size.height - trackH) / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(colors),
            topLeft = Offset(0f, top),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        val r = 6.dp.toPx()
        val x = (percent.coerceIn(0.0, 100.0) / 100.0 * size.width).toFloat().coerceIn(r, size.width - r)
        drawCircle(surface, radius = r, center = Offset(x, size.height / 2f))
        drawCircle(knobColor, radius = r, center = Offset(x, size.height / 2f), style = Stroke(width = 2.5.dp.toPx()))
    }
}

/** 恐惧贪婪五档刻度条 + 当前读数游标 + 档名。 */
@Composable
internal fun FearGreedGauge(value: Double, semantic: SemanticColors, modifier: Modifier = Modifier) {
    val zones = FearGreedZone.entries
    val zoneColors = listOf(semantic.fearVeryHigh, semantic.fearHigh, semantic.fearNeutral, semantic.fearLow, semantic.fearVeryLow)
    val knob = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    androidx.compose.foundation.layout.Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val barH = 10.dp.toPx()
            val top = (size.height - barH) / 2f
            zones.forEachIndexed { i, z ->
                val x0 = z.lower / 101f * size.width
                val x1 = (z.upper + 1) / 101f * size.width
                drawRect(zoneColors[i], topLeft = Offset(x0, top), size = Size(x1 - x0, barH))
            }
            val r = 7.dp.toPx()
            val x = ((value.coerceIn(0.0, 100.0) + 0.5) / 101.0 * size.width).toFloat().coerceIn(r, size.width - r)
            drawCircle(surface, radius = r, center = Offset(x, size.height / 2f))
            drawCircle(knob, radius = r, center = Offset(x, size.height / 2f), style = Stroke(width = 3.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            zones.forEach { z ->
                Box(Modifier.weight((z.upper - z.lower + 1).toFloat()), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(z.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 迷你走势（面积渐变 + 折线），至少 2 点。 */
@Composable
internal fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier, description: String? = null) {
    if (values.size < 2) return
    val semanticsModifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.then(semanticsModifier)) {
        val minV = values.min()
        val maxV = values.max()
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val pad = 2.dp.toPx()
        val h = size.height - 2 * pad
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, pad + (1f - ((v - minV) / span).toFloat()) * h) }
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
