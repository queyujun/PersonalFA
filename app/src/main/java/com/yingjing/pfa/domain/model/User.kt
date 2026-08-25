package com.yingjing.pfa.domain.model

/** 领域层用户（不含密码哈希等敏感字段）。 */
data class User(
    val id: Long,
    val username: String,
    val defaultCurrency: Currency,
    val createdAtEpochMs: Long,
    val nickname: String? = null,
    val gender: String? = null,
    val age: Int? = null,
)
