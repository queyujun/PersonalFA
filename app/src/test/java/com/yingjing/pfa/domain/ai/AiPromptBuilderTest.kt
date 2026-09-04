package com.yingjing.pfa.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提示词构造：消息结构（system+user）、locale 透传、payload 嵌入、追问透传。 */
class AiPromptBuilderTest {

    private val payloadJson = """{"baseCurrency":"CNY","totalAssets":1000}"""

    @Test
    fun reportMessages_hasSystemAndUser_payloadEmbedded() {
        val messages = AiPromptBuilder.reportMessages(payloadJson, "zh-CN")
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)

        // 系统消息：核心约束齐全
        val system = messages[0].content
        assertTrue(system.contains("## Executive Summary"))
        assertTrue(system.contains("## Data Limitations"))
        assertTrue(system.contains("Forbidden: tables"))
        // 渲染观感：允许 --- 分隔线，并要求关键数字用 **bold** 强调
        assertTrue(system.contains("`---`"))
        assertTrue(system.contains("**bold**"))

        // 用户消息嵌入 payload
        assertTrue(messages[1].content.contains(payloadJson))
    }

    @Test
    fun reportMessages_englishLocale_englishDirective() {
        val messages = AiPromptBuilder.reportMessages(payloadJson, "en")
        assertTrue(messages[0].content.contains("English"))
        assertTrue(messages[1].content.contains("English"))
    }

    @Test
    fun reportMessages_chineseLocale_chineseDirective() {
        val messages = AiPromptBuilder.reportMessages(payloadJson, "zh-HK")
        assertFalse(messages[0].content.contains("Respond in English"))
    }

    @Test
    fun insightMessages_containsInsightSections() {
        val messages = AiPromptBuilder.insightMessages(payloadJson, "zh-CN")
        val system = messages[0].content
        assertTrue(system.contains("## Overall Assessment"))
        assertTrue(system.contains("## Recommendations"))
        // 分析模板不应含报告专属段落
        assertFalse(system.contains("## Executive Summary"))
        assertTrue(messages[1].content.contains(payloadJson))
    }

    @Test
    fun insightMessages_userQuestion_passedThrough_andTruncated() {
        val longQuestion = "问".repeat(500)
        val messages = AiPromptBuilder.insightMessages(payloadJson, "zh-CN", userQuestion = longQuestion)
        // 截断到 200 字符，防止把 prompt 撑爆
        assertTrue(messages[1].content.contains("问".repeat(200)))
        assertFalse(messages[1].content.contains("问".repeat(201)))
    }

    @Test
    fun insightMessages_blankQuestion_ignored() {
        val messages = AiPromptBuilder.insightMessages(payloadJson, "zh-CN", userQuestion = "   ")
        assertFalse(messages[1].content.contains("especially wants"))
    }
}
