package com.yingjing.pfa.domain.alert

/** 一只可申购的新股。 */
data class IpoItem(
    val name: String,
    val applyCode: String,
    /** 申购日（yyyy-MM-dd）。 */
    val applyDate: String,
)
