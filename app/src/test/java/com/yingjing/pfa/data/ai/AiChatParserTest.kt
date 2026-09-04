package com.yingjing.pfa.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** OpenAI 兼容响应体解析：正常/空 choices/think 围栏/error.message/非法 JSON。 */
class AiChatParserTest {

    @Test
    fun parse_normalResponse_extractsContentAndModel() {
        val body = """
            {"id":"x","model":"deepseek-chat",
             "choices":[{"index":0,"message":{"role":"assistant","content":"## 报告"},"finish_reason":"stop"}]}
        """.trimIndent()
        val result = AiChatParser.parse(body)
        assertEquals("## 报告" to "deepseek-chat", result)
    }

    @Test
    fun parse_missingModel_returnsNullModel() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"hello"}}]}"""
        assertEquals("hello" to null, AiChatParser.parse(body))
    }

    @Test
    fun parse_emptyChoices_returnsNull() {
        assertNull(AiChatParser.parse("""{"choices":[]}"""))
    }

    @Test
    fun parse_missingChoices_returnsNull() {
        assertNull(AiChatParser.parse("""{"error":{"message":"bad"}}"""))
    }

    @Test
    fun parse_blankContent_returnsNull() {
        assertNull(AiChatParser.parse("""{"choices":[{"message":{"content":"   "}}]}"""))
    }

    @Test
    fun parse_invalidJson_returnsNull() {
        assertNull(AiChatParser.parse("not a json"))
        assertNull(AiChatParser.parse(""))
    }

    @Test
    fun sanitize_fencedThink_removed() {
        val text = "<think>\nlet me analyze...\n</think>\n## 结论\n- 要点"
        assertEquals("## 结论\n- 要点", AiChatParser.sanitize(text))
    }

    @Test
    fun sanitize_unclosedThinkPrefix_removed() {
        // 响应被截断时 think 未闭合
        val text = "<think>\npartial reasoning without close tag"
        assertEquals("", AiChatParser.sanitize(text))
    }

    @Test
    fun sanitize_plainText_unchanged() {
        val text = "## 报告\n- 内容"
        assertEquals(text, AiChatParser.sanitize(text))
    }

    @Test
    fun errorBody_standardFormat_extractsMessage() {
        val body = """{"error":{"message":"Incorrect API key provided.","type":"invalid_request_error"}}"""
        assertEquals("Incorrect API key provided.", AiChatParser.errorBody(body))
    }

    @Test
    fun errorBody_nonStandard_returnsNull() {
        assertNull(AiChatParser.errorBody("""{"message":"no error wrapper"}"""))
        assertNull(AiChatParser.errorBody("plain text"))
    }

    @Test
    fun errorBody_preferChinese_usesMessageZhWhenPresent() {
        val body = """{"error":{"code":"401006","message":"The service ID does not exist.",""" +
            """"message_zh":"输入的服务 ID 不存在。"}}"""
        assertEquals("输入的服务 ID 不存在。", AiChatParser.errorBody(body, preferChinese = true))
        // 不偏好中文时仍取英文 message
        assertEquals("The service ID does not exist.", AiChatParser.errorBody(body))
    }

    @Test
    fun errorBody_preferChinese_blankOrMissingMessageZh_fallsBack() {
        val body = """{"error":{"message":"Model not exist","message_zh":""}}"""
        assertEquals("Model not exist", AiChatParser.errorBody(body, preferChinese = true))
        val noZh = """{"error":{"message":"Model not exist"}}"""
        assertEquals("Model not exist", AiChatParser.errorBody(noZh, preferChinese = true))
    }

    // -------------------------------------------- Responses API 响应解析

    @Test
    fun parseResponses_topLevelOutputText_preferred() {
        val body = """
            {"id":"resp_1","model":"hy3","status":"completed",
             "output":[{"type":"message","content":[{"type":"output_text","text":"ignored"}]}],
             "output_text":"顶层文本"}
        """.trimIndent()
        assertEquals("顶层文本" to "hy3", AiChatParser.parseResponses(body))
    }

    @Test
    fun parseResponses_outputArray_extractsOutputText() {
        val body = """
            {"id":"resp_1","model":"hy3",
             "output":[{"type":"reasoning","summary":[]},
                       {"type":"message","role":"assistant",
                        "content":[{"type":"output_text","text":"## 报告内容"}]}]}
        """.trimIndent()
        assertEquals("## 报告内容" to "hy3", AiChatParser.parseResponses(body))
    }

    @Test
    fun parseResponses_missingModel_returnsNullModel() {
        val body = """{"output":[{"type":"message","content":[{"type":"output_text","text":"hi"}]}]}"""
        assertEquals("hi" to null, AiChatParser.parseResponses(body))
    }

    @Test
    fun parseResponses_emptyOrNoText_returnsNull() {
        assertNull(AiChatParser.parseResponses("""{"output":[]}"""))
        assertNull(AiChatParser.parseResponses("""{"output":[{"type":"reasoning"}]}"""))
        assertNull(AiChatParser.parseResponses("""{"output_text":"   "}"""))
        assertNull(AiChatParser.parseResponses("not a json"))
    }
}
