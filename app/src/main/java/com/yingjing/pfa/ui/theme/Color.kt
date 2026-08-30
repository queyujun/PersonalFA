package com.yingjing.pfa.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 数据语义色（跨主题保持一致，确保涨跌/分类的辨识度不随主题变化）。
 *
 * 中国市场惯例：红涨 / 绿跌，颜色固定以保证语义辨识。
 * 资产分类色为固定顺序（房产/存款/股票/黄金/国债/区块链/公司股权/场外基金/其他），
 * 走势/饼图依赖该稳定色序区分类别，不随主题切换。
 */
val GainRed = Color(0xFFD03B3B)
val LossGreen = Color(0xFF0CA30C)

// 资产分类色（固定顺序：房产/存款/股票/黄金/国债/区块链/公司股权/场外基金/其他）
val Cat1 = Color(0xFF2A78D6)
val Cat2 = Color(0xFFEB6834)
val Cat3 = Color(0xFF1BAF7A)
val Cat4 = Color(0xFFEDA100)
val Cat5 = Color(0xFFE87BA4)
val Cat6 = Color(0xFF008300)
val Cat7 = Color(0xFF4A3AA7)
val Cat8 = Color(0xFF1F9E8F)
val Cat9 = Color(0xFF6B7280)

// 自定义组合走势线（用户多选资产类别合计），紫红色，区别于上述固定分类色
val CustomCombo = Color(0xFF8E24AA)
