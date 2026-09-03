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
}
