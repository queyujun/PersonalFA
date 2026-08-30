package com.yingjing.pfa.core.validation

import androidx.annotation.StringRes

/**
 * 校验失败：持有字符串资源 id 与占位参数，由渲染端（ViewModel/Composable）
 * 经 [com.yingjing.pfa.core.i18n.StringResolver] 解析为最终文案。
 * null 表示校验通过。
 */
data class ValidationFailure(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
)
