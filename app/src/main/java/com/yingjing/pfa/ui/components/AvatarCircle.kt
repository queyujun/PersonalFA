package com.yingjing.pfa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yingjing.pfa.ui.theme.Cat1
import com.yingjing.pfa.ui.theme.Cat2
import com.yingjing.pfa.ui.theme.Cat3
import com.yingjing.pfa.ui.theme.Cat4
import com.yingjing.pfa.ui.theme.Cat5
import com.yingjing.pfa.ui.theme.Cat6
import com.yingjing.pfa.ui.theme.Cat7

private val AVATAR_COLORS = listOf(Cat1, Cat2, Cat3, Cat4, Cat5, Cat6, Cat7)

/**
 * 首字母头像：取昵称/用户名首字，背景色按名字确定性选取（无需存储图片）。
 */
@Composable
fun AvatarCircle(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val trimmed = name.trim()
    val initial = trimmed.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = if (trimmed.isEmpty()) {
        Cat1
    } else {
        val idx = ((trimmed.hashCode() % AVATAR_COLORS.size) + AVATAR_COLORS.size) % AVATAR_COLORS.size
        AVATAR_COLORS[idx]
    }

    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}
