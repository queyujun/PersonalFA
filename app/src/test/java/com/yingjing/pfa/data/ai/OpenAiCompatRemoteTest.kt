package com.yingjing.pfa.data.ai

import com.yingjing.pfa.data.ai.AiApiProtocol
import com.yingjing.pfa.domain.ai.AiFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** OpenAI 兼容 remote：状态码映射 / Bearer 头 / URL 拼接 / 请求体 / 超时 / 取消语义。 */
class OpenAiCompatRemoteTest {

    private lateinit var server: MockWebServer
    private lateinit var remote: OpenAiCompatRemote

    private val request = AiChatRequest(
        baseUrl = "http://placeholder", // setup 中替换
        apiKey = "sk-test-key",
        model = "deepseek-chat",
        messages = listOf(
            AiChatMessage("system", "You are a financial advisor."),
            AiChatMessage("user", "Generate report"),
        ),
    )

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        remote = OpenAiCompatRemote(OkHttpClient.Builder()
            .readTimeout(2, TimeUnit.SECONDS)
            .build())
        remote.chatCompletionsSuffix = "/chat/completions"
        remote.responsesSuffix = "/responses"
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun okBody() = """
        {"model":"deepseek-chat","choices":[{"message":{"role":"assistant","content":"## 报告内容"}}]}
    """.trimIndent()

    @Test
    fun success_returnsTextAndModel() = runBlocking {
        server.enqueue(MockResponse().setBody(okBody()))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        val success = result as com.yingjing.pfa.domain.ai.AiChatResult.Success
        assertEquals("## 报告内容", success.text)
        assertEquals("deepseek-chat", success.model)
    }

    @Test
    fun request_hasBearerHeaderAndJsonBody() = runBlocking {
        server.enqueue(MockResponse().setBody(okBody()))
        remote.complete(request.copy(baseUrl = server.url("/v1").toString()))

        val recorded = server.takeRequest()
        assertEquals("Bearer sk-test-key", recorded.getHeader("Authorization"))
        assertEquals("/v1/chat/completions", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"deepseek-chat\""))
        assertTrue(body.contains("\"max_tokens\":3072"))
        assertTrue(body.contains("You are a financial advisor."))
        assertTrue(body.contains("\"role\":\"system\""))
    }

    @Test
    fun unauthorized_401_mapsToUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401)
            .setBody("""{"error":{"message":"Invalid API key"}}"""))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        val failure = result as com.yingjing.pfa.domain.ai.AiChatResult.Failure
        assertEquals(AiFailureKind.UNAUTHORIZED, failure.kind)
        assertEquals("Invalid API key", failure.detail)
    }

    @Test
    fun rateLimited_429_mapsToRateLimited() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"slow down"}}"""))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        assertEquals(AiFailureKind.RATE_LIMITED, (result as com.yingjing.pfa.domain.ai.AiChatResult.Failure).kind)
    }

    @Test
    fun serverError_500_mapsToServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        assertEquals(AiFailureKind.SERVER_ERROR, (result as com.yingjing.pfa.domain.ai.AiChatResult.Failure).kind)
    }

    @Test
    fun badRequest_400_mapsToBadRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400)
            .setBody("""{"error":{"message":"Model not exist"}}"""))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        val failure = result as com.yingjing.pfa.domain.ai.AiChatResult.Failure
        assertEquals(AiFailureKind.BAD_REQUEST, failure.kind)
        assertEquals("Model not exist", failure.detail)
    }

    @Test
    fun badRequest_400_chineseLocale_prefersMessageZh() = runBlocking {
        remote.preferChineseError = true
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"error":{"type":"gateway_error","code":"401006",""" +
                """"message":"The service ID does not exist.",""" +
                """"message_zh":"输入的服务 ID 不存在，或模型与服务不匹配。"}}""",
        ))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        val failure = result as com.yingjing.pfa.domain.ai.AiChatResult.Failure
        assertEquals(AiFailureKind.BAD_REQUEST, failure.kind)
        assertEquals("输入的服务 ID 不存在，或模型与服务不匹配。", failure.detail)
    }

    @Test
    fun badRequest_400_messageZhBlankOrMissing_fallsBackToMessage() = runBlocking {
        remote.preferChineseError = true
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"error":{"message":"Model not exist","message_zh":""}}""",
        ))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        val failure = result as com.yingjing.pfa.domain.ai.AiChatResult.Failure
        assertEquals(AiFailureKind.BAD_REQUEST, failure.kind)
        assertEquals("Model not exist", failure.detail)
    }

    @Test
    fun successButUnparseable_mapsToEmptyResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        assertEquals(AiFailureKind.EMPTY_RESPONSE, (result as com.yingjing.pfa.domain.ai.AiChatResult.Failure).kind)
    }

    // ---------------------------------------------------- Responses 协议

    /** Responses 协议成功响应体（OpenAI Responses API 结构）。 */
    private fun responsesOkBody() = """
        {"id":"resp_1","model":"hy3","status":"completed",
         "output":[{"type":"reasoning","content":[]},
                   {"type":"message","role":"assistant",
                    "content":[{"type":"output_text","text":"## 分析结论","annotations":[]}]}],
         "output_text":"## 分析结论"}
    """.trimIndent()

    @Test
    fun responsesProtocol_postsToResponsesEndpointWithInstructionsAndInput() = runBlocking {
        server.enqueue(MockResponse().setBody(responsesOkBody()))
        val result = remote.complete(
            request.copy(
                baseUrl = server.url("/v1").toString(),
                protocol = AiApiProtocol.RESPONSES,
            ),
        )
        val success = result as com.yingjing.pfa.domain.ai.AiChatResult.Success
        assertEquals("## 分析结论", success.text)
        assertEquals("hy3", success.model)

        val recorded = server.takeRequest()
        assertEquals("/v1/responses", recorded.path)
        assertEquals("Bearer sk-test-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        // 请求 model 取自 request.model（deepseek-chat）；"hy3" 是 mock 响应体里的 model
        assertTrue(body.contains("\"model\":\"deepseek-chat\""))
        assertTrue(body.contains("\"instructions\":\"You are a financial advisor.\""))
        assertTrue(body.contains("\"input\":\"Generate report\""))
        assertTrue(body.contains("\"max_output_tokens\":6144"))
        assertTrue(body.contains("\"stream\":false"))
        // Chat Completions 专属字段不应出现
        assertTrue(!body.contains("max_tokens"))
        assertTrue(!body.contains("messages"))
    }

    @Test
    fun responsesProtocol_parsesOutputArrayWhenNoTopLevelText() = runBlocking {
        // 部分网关不回 output_text 顶层字段，只能从 output[].content[] 里取
        server.enqueue(MockResponse().setBody(
            """{"model":"hy3","status":"completed",
                "output":[{"type":"message","role":"assistant",
                 "content":[{"type":"output_text","text":"来自 output 数组的文本"}]}]}""",
        ))
        val result = remote.complete(
            request.copy(
                baseUrl = server.url("/v1").toString(),
                protocol = AiApiProtocol.RESPONSES,
            ),
        )
        val success = result as com.yingjing.pfa.domain.ai.AiChatResult.Success
        assertEquals("来自 output 数组的文本", success.text)
    }

    @Test
    fun responsesProtocol_unparseableBody_mapsToEmptyResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"output":[]}"""))
        val result = remote.complete(
            request.copy(
                baseUrl = server.url("/v1").toString(),
                protocol = AiApiProtocol.RESPONSES,
            ),
        )
        assertEquals(AiFailureKind.EMPTY_RESPONSE, (result as com.yingjing.pfa.domain.ai.AiChatResult.Failure).kind)
    }

    @Test
    fun responsesProtocol_badRequest_mapsToBadRequestWithDetail() = runBlocking {
        remote.preferChineseError = true
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"error":{"code":"401006","message":"The service ID does not exist.",""" +
                """"message_zh":"输入的服务 ID 不存在。"}}""",
        ))
        val result = remote.complete(
            request.copy(
                baseUrl = server.url("/v1").toString(),
                protocol = AiApiProtocol.RESPONSES,
            ),
        )
        val failure = result as com.yingjing.pfa.domain.ai.AiChatResult.Failure
        assertEquals(AiFailureKind.BAD_REQUEST, failure.kind)
        assertEquals("输入的服务 ID 不存在。", failure.detail)
    }

    @Test
    fun noDisconnect_timeout_mapsToTimeout() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
        assertEquals(AiFailureKind.TIMEOUT, (result as com.yingjing.pfa.domain.ai.AiChatResult.Failure).kind)
    }

    @Test
    fun serverShutdown_mapsToNetwork() = runBlocking {
        server.shutdown()
        val result = remote.complete(request.copy(baseUrl = "http://127.0.0.1:1/v1"))
        assertEquals(AiFailureKind.NETWORK, (result as com.yingjing.pfa.domain.ai.AiChatResult.Failure).kind)
    }

    @Test
    fun cancellation_propagatesCancellationException() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            coroutineScope {
                val job = async {
                    remote.complete(request.copy(baseUrl = server.url("/v1").toString()))
                }
                job.cancel()
                job.await()
            }
            throw AssertionError("expected CancellationException")
        } catch (expected: CancellationException) {
            // 取消必须原样上抛（用户点停止 ≠ 网络错误）
            assertTrue(true)
        }
    }
}
