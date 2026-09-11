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

    // -------------------------------------------- SSE 流式解析（parseSseData）

    @Test
    fun parseSseData_chat_deltaAndModelExtracted() {
        val chunk = AiChatParser.parseSseData(
            """{"model":"deepseek-chat","choices":[{"delta":{"content":"## 概览"}}]}""",
            AiApiProtocol.CHAT_COMPLETIONS,
        )
        assertEquals(AiSseChunk("## 概览", "deepseek-chat", null), chunk)
    }

    @Test
    fun parseSseData_chat_reasoningContent_skipped() {
        // 推理模型的 reasoning_content 是思考过程，不能混入正文
        val chunk = AiChatParser.parseSseData(
            """{"model":"deepseek-chat","choices":[{"delta":{"reasoning_content":"思考中"}}]}""",
            AiApiProtocol.CHAT_COMPLETIONS,
        )
        assertEquals(AiSseChunk(null, "deepseek-chat", null), chunk)
    }

    @Test
    fun parseSseData_chat_emptyDelta_returnsNull() {
        assertNull(
            AiChatParser.parseSseData(
                """{"choices":[{"delta":{}}]}""",
                AiApiProtocol.CHAT_COMPLETIONS,
            ),
        )
    }

    @Test
    fun parseSseData_responses_textDelta_extracted() {
        val chunk = AiChatParser.parseSseData(
            """{"type":"response.output_text.delta","delta":"部分正文"}""",
            AiApiProtocol.RESPONSES,
        )
        assertEquals(AiSseChunk("部分正文", null, null), chunk)
    }

    @Test
    fun parseSseData_responses_created_carriesModel() {
        val chunk = AiChatParser.parseSseData(
            """{"type":"response.created","response":{"id":"resp_1","model":"hy3"}}""",
            AiApiProtocol.RESPONSES,
        )
        assertEquals(AiSseChunk(null, "hy3", null), chunk)
    }

    @Test
    fun parseSseData_responses_doneEvent_ignored() {
        // *.done 收尾事件携带全文：不得当作增量追加，否则内容翻倍
        assertNull(
            AiChatParser.parseSseData(
                """{"type":"response.output_text.done","text":"全文全文"}""",
                AiApiProtocol.RESPONSES,
            ),
        )
    }

    @Test
    fun parseSseData_doneSentinelAndBadLine_ignored() {
        assertNull(AiChatParser.parseSseData("[DONE]", AiApiProtocol.CHAT_COMPLETIONS))
        assertNull(AiChatParser.parseSseData("", AiApiProtocol.CHAT_COMPLETIONS))
        assertNull(AiChatParser.parseSseData("   ", AiApiProtocol.RESPONSES))
        // TCP 分块截断的半截 JSON：静默跳过不断流
        assertNull(
            AiChatParser.parseSseData(
                """{"model":"deepseek-chat","choices":[{"delta":{"content":"断""",
                AiApiProtocol.CHAT_COMPLETIONS,
            ),
        )
    }

    @Test
    fun parseSseData_inStreamError_carriesMessage() {
        val chunk = AiChatParser.parseSseData(
            """{"error":{"message":"Model not exist","message_zh":"模型不存在。"}}""",
            AiApiProtocol.CHAT_COMPLETIONS,
        )
        assertEquals(AiSseChunk(null, null, "模型不存在。"), chunk)
    }

    @Test
    fun parseSseData_nullDeltaFields_treatedAsMissing() {
        // 个别网关显式回 JSON null：不得当作 "null" 文本追加
        val chunk = AiChatParser.parseSseData(
            """{"model":null,"choices":[{"delta":{"content":null}}]}""",
            AiApiProtocol.CHAT_COMPLETIONS,
        )
        assertNull(chunk)
    }

    // -------------------------------------------- 截断信号（max_tokens 用尽）

    @Test
    fun parseSseData_chat_finishReasonLength_onlyInTailChunk_carriesTruncated() {
        // 流式收尾块常只有 finish_reason 没有 delta/model：截断信号不能被整块丢弃
        val chunk = AiChatParser.parseSseData(
            """{"choices":[{"finish_reason":"length"}]}""",
            AiApiProtocol.CHAT_COMPLETIONS,
        )
        assertEquals(AiSseChunk(null, null, null, truncated = true), chunk)
    }

    @Test
    fun parseSseData_chat_finishReasonStop_ignored() {
        // 正常结束 finish_reason=stop：不是截断，无须产生事件
        assertNull(
            AiChatParser.parseSseData(
                """{"choices":[{"finish_reason":"stop"}]}""",
                AiApiProtocol.CHAT_COMPLETIONS,
            ),
        )
    }

    @Test
    fun parseSseData_chat_deltaWithFinishReasonLength_keepsBoth() {
        // 个别网关把 finish_reason 附在最后一个增量块上：增量与截断信号并存
        val chunk = AiChatParser.parseSseData(
            """{"model":"deepseek-chat","choices":[{"delta":{"content":"尾段"},"finish_reason":"length"}]}""",
            AiApiProtocol.CHAT_COMPLETIONS,
        )
        assertEquals(AiSseChunk("尾段", "deepseek-chat", null, truncated = true), chunk)
    }

    @Test
    fun parseSseData_responses_statusIncomplete_carriesTruncated() {
        // response.completed / response.incomplete 收尾事件携带 response.status=incomplete
        val chunk = AiChatParser.parseSseData(
            """{"type":"response.completed","response":{"id":"resp_1","status":"incomplete"}}""",
            AiApiProtocol.RESPONSES,
        )
        assertEquals(AiSseChunk(null, null, null, truncated = true), chunk)
    }

    @Test
    fun parseSseData_responses_statusCompleted_ignored() {
        // 正常完成 status=completed：无增量无截断，不产生事件
        assertNull(
            AiChatParser.parseSseData(
                """{"type":"response.completed","response":{"id":"resp_1","status":"completed"}}""",
                AiApiProtocol.RESPONSES,
            ),
        )
    }

    @Test
    fun parseSseData_responses_deltaWithIncompleteStatus_flagsTruncated() {
        // 思考占满预算时截断状态可先于正文增量到达：增量照常、truncated 置位
        val chunk = AiChatParser.parseSseData(
            """{"type":"response.output_text.delta","delta":"部分","response":{"status":"incomplete"}}""",
            AiApiProtocol.RESPONSES,
        )
        assertEquals(AiSseChunk("部分", null, null, truncated = true), chunk)
    }
}
