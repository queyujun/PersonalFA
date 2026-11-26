package com.yingjing.pfa.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 数据语义色浅色常量：涨跌 / 分类 / 自定义组合（跨主题保持一致，确保辨识度不随主题变化）。
 *
 * 中国市场惯例：红涨 / 绿跌，颜色固定以保证语义辨识。
 * 资产分类色为固定顺序（房产/存款/股票/黄金/国债/区块链/公司股权/场外基金/其他），
 * 走势/饼图依赖该稳定色序区分类别。
 *
 * 深色模式取 [SemanticColors]（深色套为同色相提亮变体）：浅色值不变（零回归），
 * UI 取色应经 [LocalSemanticColors]，不直接引用这些常量（[BrandColors.kt] 与
 * [AvatarCircle] 为既有浅色/确定性用法，保持引用）。
 */
val GainRed = Color(0xFFD03B3B)
val LossGreen = Color(0xFF0CA30C)

// 资产分类色（固定顺序：房产/存款/股票/黄金/国债/区块链/公司股权/场外基金/其他）
// 浅色调 + 中等饱和：鲜明易辨又不艳丽刺眼，九色色相错开保证辨识度。
val Cat1 = Color(0xFF6FA8D8)  // 房产 — 浅天蓝
val Cat2 = Color(0xFFE8A878)  // 存款 — 浅橙
val Cat3 = Color(0xFF74C498)  // 股票 — 浅草绿
val Cat4 = Color(0xFFECC56A)  // 黄金 — 浅金黄
val Cat5 = Color(0xFFDC8CA6)  // 国债 — 浅玫粉
val Cat6 = Color(0xFFB8C75A)  // 区块链 — 浅黄绿
val Cat7 = Color(0xFF9183D6)  // 公司股权 — 浅紫
val Cat8 = Color(0xFF5DC4B0)  // 场外基金 — 浅松青
val Cat9 = Color(0xFF9DA4AE)  // 其他 — 浅灰蓝

// 自定义组合走势线（用户多选资产类别合计），浅紫红，区别于上述固定分类色
val CustomCombo = Color(0xFFBD60B5)

// 恐慌程度五档（浅色）：平静绿 → 偏低黄绿 → 中性灰 → 偏高橙 → 极高红
val FearVeryLow = Color(0xFF2F9E6F)
val FearLow = Color(0xFF7FB35A)
val FearNeutral = Color(0xFF8E959F)
val FearHigh = Color(0xFFE0782F)
val FearVeryHigh = Color(0xFFCF3B30)

// 恐慌指数走势叠加价格线（琥珀）
val OverlayLine = Color(0xFFD08A1E)
