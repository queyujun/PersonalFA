package com.yingjing.pfa.domain.model

/** AI 生成结果记录（领域层视图；kind 取值与实体层一致：report / insight）。 */
data class AiReportRecord(
    val id: Long,
    val userId: Long,
    val kind: String,
    val title: String,
    val model: String?,
    val markdown: String,
    val createdAt: Long,
)
