package com.yingjing.pfa.fakes

import com.yingjing.pfa.core.i18n.StringResolver

/**
 * 测试用 [StringResolver]：不依赖 Android 资源，纯 JVM 可跑。
 *
 * - 无参：返回 `"res<id>"` —— 一个非空且能区分不同资源键的稳定标识，
 *   便于断言「是否触发了某错误键」（不绑 locale，i18n 测试只验键不验翻译）。
 * - 有参：用空格连接参数 —— 规则测试只关心阈值/严重度/去重键/分类等结构属性，
 *   以及参数数据（持仓名、百分比、数量）是否正确落入文案。
 */
class FakeStringResolver : StringResolver {
    override fun get(resId: Int): String = "res$resId"
    override fun get(resId: Int, vararg args: Any): String = args.joinToString(" ")
}
