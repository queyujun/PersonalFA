package com.yingjing.pfa.domain.ai

import com.yingjing.pfa.data.ai.AiChatMessage
import com.yingjing.pfa.data.ai.AiReportTone

/**
 * AI 提示词构造：报告与分析两套专业模板 × 两种语气档（按 locale 双语）。
 *
 * 约束要点：只用给定数据、统一基准币种、显式假设、仅 Markdown `##`/`###` 标题 + 要点列表 +
 * `---` 分隔线、关键数字用 `**加粗**` 强调（禁止表格与代码围栏，便于应用内轻量渲染）、结尾免责声明。
 *
 * 语气档只改叙事口吻（ANALYST = CFP 分析师；COMPANION = 安静的老派管家，承认坚持、
 * 指出变化而非只给结论），数据与格式硬规则两档一致。
 */
object AiPromptBuilder {

    /** 生成个人资产报告的完整消息列表。 */
    fun reportMessages(payloadJson: String, localeTag: String, tone: AiReportTone = AiReportTone.ANALYST): List<AiChatMessage> =
        buildMessages(reportSystem(localeTag, tone), reportUser(payloadJson, localeTag))

    /** 持仓分析的消息列表；[userQuestion] 为可选的用户追问。 */
    fun insightMessages(
        payloadJson: String,
        localeTag: String,
        userQuestion: String? = null,
        tone: AiReportTone = AiReportTone.ANALYST,
    ): List<AiChatMessage> =
        buildMessages(insightSystem(localeTag, tone), insightUser(payloadJson, userQuestion))

    private fun buildMessages(system: String, user: String): List<AiChatMessage> = listOf(
        AiChatMessage(role = "system", content = system),
        AiChatMessage(role = "user", content = user),
    )

    // ---------------------------------------------------------------- system

    private fun systemPreamble(localeTag: String, tone: AiReportTone): String {
        val lang = if (localeTag.startsWith("zh")) "简体中文" else "English"
        val persona = if (tone == AiReportTone.COMPANION) {
            """
            You are a trusted personal finance steward — a quiet, old-school butler who has kept
            the family's books for years. You speak plainly, with warmth and restraint.
            Tone rules (in addition to the rules below):
            - Open by talking to the reader about their month/period in a natural narrative sentence,
              not with a label or a heading.
            - Acknowledge effort and consistency where the data shows it (steady deposits kept,
              positions held through volatility, liabilities paid down) — one honest sentence,
              never flattery.
            - Point out changes over time rather than only stating conclusions: what moved,
              in which direction, roughly by how much.
            - Stay measured and understated. Never exclaim, never cheerlead, no emoji,
              no motivational slogans. The comfort comes from being seen, not from praise.
            - When something looks off, say it gently but clearly — candor is part of the care.
            """.trimIndent()
        } else {
            """
            You are a certified financial planning advisor (CFP-style).
            """.trimIndent()
        }
        return """
            $persona Respond in $lang.
            Rules:
            1. Use ONLY the data provided in the JSON. Never invent or assume numbers that are not given.
            2. All amounts are in the base_currency stated in the JSON. State this unit explicitly when citing figures.
            3. When data is missing or ambiguous, state your assumptions explicitly.
            4. Output plain Markdown ONLY with `##` / `###` headings, bullet lists (`-`) or numbered lists,
               and `---` horizontal rules to separate major sections.
               Forbidden: tables, code fences, HTML.
            5. Emphasize key figures, ratios and conclusions with `**bold**` (e.g. `**42.3%**`).
               Use them sparingly — only for the numbers or verdicts the reader must notice.
            6. Be specific: cite actual figures and percentages from the data. Avoid generic advice.
            7. End with a short disclaimer that this is AI-generated analysis, not professional investment advice.
        """.trimIndent()
    }

    private fun reportSystem(localeTag: String, tone: AiReportTone): String {
        val narrative = if (tone == AiReportTone.COMPANION) {
            """
            Under the opening heading, start with one or two narrative sentences about how this
            period looks for the reader (what changed since the last snapshot the series shows,
            what stayed steady) before the bullets.
            """.trimIndent()
        } else {
            ""
        }
        return systemPreamble(localeTag, tone) + "\n\n" + """
            Produce a personal asset report following EXACTLY this section order:
            ## Executive Summary
            ## Asset & Liability Structure
            ## Asset Allocation Analysis
            ## Returns & Costs
            ## Risk Assessment
            ## Improvement Suggestions
            ## Data Limitations

            Guidance per section:
            - Executive Summary: 3-5 bullets; bold the net worth figure and the single most important observation.
              $narrative
            - Asset & Liability Structure: asset/liability ratio, composition by category, liquidity observation.
            - Asset Allocation Analysis: top-3 concentration, cross-market and currency exposure, diversification quality.
            - Returns & Costs: observable P/L from the data, deposit interest rates, possible fee/cost blind spots.
            - Risk Assessment: rate market risk / concentration risk / liquidity risk / currency risk / solvency risk,
              each rated **高·中·低** (or **High·Medium·Low**) with one-line justification.
            - Improvement Suggestions: prioritized P1/P2/P3, each with a concrete action AND the reason.
            - Data Limitations: what the data does NOT tell you.
        """.trimIndent()
    }

    private fun insightSystem(localeTag: String, tone: AiReportTone): String {
        val narrative = if (tone == AiReportTone.COMPANION) {
            """
            The one-sentence rating may be woven into a natural opening sentence about the
            reader's situation; keep the rating itself bolded.
            """.trimIndent()
        } else {
            ""
        }
        return systemPreamble(localeTag, tone) + "\n\n" + """
            Produce a portfolio insight review following EXACTLY this section order:
            ## Overall Assessment
            ## Structure Diagnosis
            ## Returns & Costs
            ## Risk Alerts
            ## Recommendations
            ## Suggested Additional Information

            Guidance per section:
            - Overall Assessment: one-sentence rating (**优/良/中/差** or **Excellent/Good/Fair/Poor**) + justification.
              $narrative
            - Structure Diagnosis: category weights vs. common diversification benchmarks, concentration, currency exposure.
            - Returns & Costs: observable P/L, yield sources, cost blind spots.
            - Risk Alerts: only risks actually supported by the data, ordered by severity.
            - Recommendations: concrete actions with reference ratios/amounts derived from the data.
            - Suggested Additional Information: 2-4 data points the user could add to enable deeper analysis.
        """.trimIndent()
    }

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
