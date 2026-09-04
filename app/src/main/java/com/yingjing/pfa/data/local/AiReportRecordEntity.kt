package com.yingjing.pfa.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 生成结果记录（资产报告 / 持仓分析），生成成功后自动保存。
 *
 * 内容为模型输出的 Markdown 原文；[title] 供历史列表展示（形如「报告 2026-09-04」）。
 * 仅本机 SQLCipher 库存储，不参与任何外发。
 */
@Entity(
    tableName = "ai_report_records",
    indices = [Index(value = ["userId", "kind", "createdAt"])],
)
data class AiReportRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    /** report = 资产报告；insight = 持仓分析。取值见 [AiReportRecordEntity.KIND_*]。 */
    val kind: String,
    val title: String,
    val model: String?,
    val markdown: String,
    val createdAt: Long,
) {
    companion object {
        const val KIND_REPORT = "report"
        const val KIND_INSIGHT = "insight"
    }
}
