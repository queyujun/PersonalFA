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
import javax.inject.Inject
import javax.inject.Singleton

/** AI 补全远程接口（OpenAI 兼容 chat completions）。 */
interface AiRemote {
    suspend fun complete(request: AiChatRequest): AiChatResult
}

/**
 * OpenAI 兼容协议实现。
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

    /** 端点后缀（测试可覆盖为 MockWebServer 路径）。 */
    var endpointSuffix: String = "/chat/completions"

    private val json = Json { encodeDefaults = true }

    override suspend fun complete(request: AiChatRequest): AiChatResult {
        val baseUrl = request.baseUrl.trim().trimEnd('/')
        val url = baseUrl + endpointSuffix

        val payload = buildJsonObject {
            put("model", request.model)
            put("messages", json.encodeToJsonElement(ListSerializer(AiChatMessage.serializer()), request.messages))
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
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
                    when {
                        response.isSuccessful -> parseSuccess(bodyText)
                        response.code == 401 || response.code == 403 ->
                            AiChatResult.Failure(AiFailureKind.UNAUTHORIZED, AiChatParser.errorBody(bodyText))
                        response.code == 429 ->
                            AiChatResult.Failure(AiFailureKind.RATE_LIMITED, AiChatParser.errorBody(bodyText))
                        response.code in 500..599 ->
                            AiChatResult.Failure(AiFailureKind.SERVER_ERROR, AiChatParser.errorBody(bodyText))
                        else ->
                            // 400 等：服务商的错误说明（模型名不存在 / 余额不足）对用户最有用
                            AiChatResult.Failure(AiFailureKind.BAD_REQUEST, AiChatParser.errorBody(bodyText) ?: bodyText.take(300))
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

    private fun parseSuccess(bodyText: String): AiChatResult {
        val parsed = AiChatParser.parse(bodyText) ?: return AiChatResult.Failure(AiFailureKind.EMPTY_RESPONSE)
        return AiChatResult.Success(text = parsed.first, model = parsed.second, promptTokens = null, completionTokens = null)
    }
}
