package com.yingjing.pfa.data.ai

import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** AI 补全远程接口（OpenAI 兼容 chat completions）。 */
interface AiRemote {
    suspend fun complete(request: AiChatRequest): AiChatResult
}

/**
 * OpenAI 兼容协议实现（Chat Completions 与 Responses 双协议）。
 *
 * 与项目内其他 remote 的「吞错返回空」契约刻意不同：AI 结果直接面向用户，
 * 逐状态码显式映射错误（401 → key 无效、429 → 限流、5xx → 服务端错误……），
 * 便于 UI 给出可操作的提示。CancellationException 必须原样上抛（用户点取消 ≠ 网络错误）。
 *
 * 不附加 logging interceptor（避免 prompt / 报告内容进入日志）。
 */
@Singleton
class OpenAiCompatRemote @Inject constructor(
    @com.yingjing.pfa.di.AiClient private val client: OkHttpClient,
) : AiRemote {

    /** Chat Completions 端点后缀（测试可覆盖为 MockWebServer 路径）。 */
    var chatCompletionsSuffix: String = "/chat/completions"

    /** Responses 端点后缀（测试可覆盖为 MockWebServer 路径）。 */
    var responsesSuffix: String = "/responses"

    /** 中文环境优先取服务商错误体里的 message_zh（如 TokenHub）。 */
    var preferChineseError: Boolean = Locale.getDefault().language == "zh"

    private val json = Json { encodeDefaults = true }

    override suspend fun complete(request: AiChatRequest): AiChatResult {
        val baseUrl = request.baseUrl.trim().trimEnd('/')
        val url = baseUrl + when (request.protocol) {
            AiApiProtocol.CHAT_COMPLETIONS -> chatCompletionsSuffix
            AiApiProtocol.RESPONSES -> responsesSuffix
        }

        val payload = when (request.protocol) {
            AiApiProtocol.CHAT_COMPLETIONS -> buildJsonObject {
                put("model", request.model)
                put("messages", json.encodeToJsonElement(ListSerializer(AiChatMessage.serializer()), request.messages))
                put("max_tokens", request.maxTokens)
                put("temperature", request.temperature)
            }
            AiApiProtocol.RESPONSES -> buildJsonObject {
                put("model", request.model)
                // Responses 协议：system 进 instructions，用户消息为 input；非流式，便于整体解析。
                put(
                    "instructions",
                    request.messages.firstOrNull { it.role == "system" }?.content.orEmpty(),
                )
                put("input", request.messages.filter { it.role != "system" }.joinToString("\n\n") { it.content })
                // 混合推理模型（如腾讯 hy3）的思考 token 也计入 max_output_tokens：
                // 实测思考常占 90%+ 预算，3072 会导致正文被截断甚至无正文（status=incomplete）。
                put("max_output_tokens", request.maxTokens * 2)
                put("stream", false)
            }
        }.toString()

        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(httpRequest).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    val errorDetail = { AiChatParser.errorBody(bodyText, preferChineseError) }
                when {
                    response.isSuccessful -> parseSuccess(bodyText, request.protocol)
                    response.code == 401 || response.code == 403 ->
                        AiChatResult.Failure(AiFailureKind.UNAUTHORIZED, errorDetail())
                    response.code == 429 ->
                        AiChatResult.Failure(AiFailureKind.RATE_LIMITED, errorDetail())
                    response.code in 500..599 ->
                        AiChatResult.Failure(AiFailureKind.SERVER_ERROR, errorDetail())
                    else ->
                        // 400 等：服务商的错误说明（模型名不存在 / 余额不足）对用户最有用
                        AiChatResult.Failure(
                            AiFailureKind.BAD_REQUEST,
                            errorDetail() ?: bodyText.take(300),
                        )
                }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                AiChatResult.Failure(AiFailureKind.TIMEOUT, e.message)
            } catch (e: IOException) {
                AiChatResult.Failure(AiFailureKind.NETWORK, e.message)
            }
        }
    }

    private fun parseSuccess(bodyText: String, protocol: AiApiProtocol): AiChatResult {
        val parsed = when (protocol) {
            AiApiProtocol.CHAT_COMPLETIONS -> AiChatParser.parse(bodyText)
            AiApiProtocol.RESPONSES -> AiChatParser.parseResponses(bodyText)
        } ?: return AiChatResult.Failure(AiFailureKind.EMPTY_RESPONSE)
        return AiChatResult.Success(text = parsed.first, model = parsed.second, promptTokens = null, completionTokens = null)
    }
}
