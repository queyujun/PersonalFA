package com.yingjing.pfa.domain.model

/** 某自然日某类别的金额（用于分类走势）。 */
data class CategoryPoint(
    val epochDay: Long,
    val category: String,
    val amount: Double,
)
