package com.yingjing.pfa.domain.ai

import com.yingjing.pfa.data.ai.AiChatMessage

/**
 * AI 提示词构造：报告与分析两套专业模板（按 locale 双语）。
 *
 * 约束要点：只用给定数据、统一基准币种、显式假设、仅 Markdown `##`/`###` 标题 + 要点列表
 * （禁止表格与代码围栏，便于应用内轻量渲染）、结尾免责声明。
 */
object AiPromptBuilder {

    /** 生成个人资产报告的完整消息列表。 */
    fun reportMessages(payloadJson: String, localeTag: String): List<AiChatMessage> =
        buildMessages(reportSystem(localeTag), reportUser(payloadJson, localeTag))

    /** 持仓分析的消息列表；[userQuestion] 为可选的用户追问。 */
    fun insightMessages(payloadJson: String, localeTag: String, userQuestion: String? = null): List<AiChatMessage> =
        buildMessages(insightSystem(localeTag), insightUser(payloadJson, userQuestion))

    private fun buildMessages(system: String, user: String): List<AiChatMessage> = listOf(
        AiChatMessage(role = "system", content = system),
        AiChatMessage(role = "user", content = user),
    )

    // ---------------------------------------------------------------- system

    private fun systemPreamble(localeTag: String): String {
        val lang = if (localeTag.startsWith("zh")) "简体中文" else "English"
        return """
            You are a certified financial planning advisor (CFP-style). Respond in $lang.
            Rules:
            1. Use ONLY the data provided in the JSON. Never invent or assume numbers that are not given.
            2. All amounts are in the base_currency stated in the JSON. State this unit explicitly when citing figures.
            3. When data is missing or ambiguous, state your assumptions explicitly.
            4. Output plain Markdown ONLY with `##` / `###` headings and bullet lists (`-`) or numbered lists.
               Forbidden: tables, code fences, HTML.
            5. Be specific: cite actual figures and percentages from the data. Avoid generic advice.
            6. End with a short disclaimer that this is AI-generated analysis, not professional investment advice.
        """.trimIndent()
    }

    private fun reportSystem(localeTag: String): String =
        systemPreamble(localeTag) + "\n\n" + """
            Produce a personal asset report following EXACTLY this section order:
            ## Executive Summary
            ## Asset & Liability Structure
            ## Asset Allocation Analysis
            ## Returns & Costs
            ## Risk Assessment
            ## Improvement Suggestions
            ## Data Limitations

            Guidance per section:
            - Executive Summary: 3-5 bullets; net worth, overall structure, the single most important observation.
            - Asset & Liability Structure: asset/liability ratio, composition by category, liquidity observation.
            - Asset Allocation Analysis: top-3 concentration, cross-market and currency exposure, diversification quality.
            - Returns & Costs: observable P/L from the data, deposit interest rates, possible fee/cost blind spots.
            - Risk Assessment: rate market risk / concentration risk / liquidity risk / currency risk / solvency risk,
              each rated 高·中·低 (or High·Medium·Low) with one-line justification.
            - Improvement Suggestions: prioritized P1/P2/P3, each with a concrete action AND the reason.
            - Data Limitations: what the data does NOT tell you.
        """.trimIndent()

    private fun insightSystem(localeTag: String): String =
        systemPreamble(localeTag) + "\n\n" + """
            Produce a portfolio insight review following EXACTLY this section order:
            ## Overall Assessment
            ## Structure Diagnosis
            ## Returns & Costs
            ## Risk Alerts
            ## Recommendations
            ## Suggested Additional Information

            Guidance per section:
            - Overall Assessment: one-sentence rating (优/良/中/差 or Excellent/Good/Fair/Poor) + justification.
            - Structure Diagnosis: category weights vs. common diversification benchmarks, concentration, currency exposure.
            - Returns & Costs: observable P/L, yield sources, cost blind spots.
            - Risk Alerts: only risks actually supported by the data, ordered by severity.
            - Recommendations: concrete actions with reference ratios/amounts derived from the data.
            - Suggested Additional Information: 2-4 data points the user could add to enable deeper analysis.
        """.trimIndent()

    // ------------------------------------------------------------------ user

    private fun reportUser(payloadJson: String, localeTag: String): String {
        val langNote = if (localeTag.startsWith("zh")) "请用简体中文撰写全文。" else "Write the entire report in English."
        return """
            Below is the JSON snapshot of a personal investment portfolio.
            Generate the full personal asset report.

            $payloadJson

            $langNote
        """.trimIndent()
    }

    private fun insightUser(payloadJson: String, userQuestion: String?): String {
        val focus = userQuestion?.takeIf { it.isNotBlank() }
            ?.let { "The user especially wants your view on: \"${it.trim().take(200)}\"\n" } ?: ""
        return """
            Below is the JSON snapshot of a personal investment portfolio.
            Analyze the current holdings and allocation, then give professional judgement and suggestions.
            ${focus}
            $payloadJson
        """.trimIndent()
    }
}
