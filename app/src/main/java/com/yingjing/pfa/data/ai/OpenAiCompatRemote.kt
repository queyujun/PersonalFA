package com.yingjing.pfa.data.ai

import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiFailureKind
import com.yingjing.pfa.domain.ai.AiStreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** AI 补全远程接口（OpenAI 兼容）。 */
interface AiRemote {
    /** 一次性补全（设置页「测试连接」等场景）。 */
    suspend fun complete(request: AiChatRequest): AiChatResult

    /**
     * 流式补全：正文增量按到达顺序发出（Delta / Model），
     * 以 [AiStreamEvent.Completed]（正常结束）或 [AiStreamEvent.Failed]（失败）收尾。
     */
    fun stream(request: AiChatRequest): Flow<AiStreamEvent>
}

/**
 * OpenAI 兼容协议实现（Chat Completions 与 Responses 双协议，流式 + 一次性补全）。
 *
 * 与项目内其他 remote 的「吞错返回空」契约刻意不同：AI 结果直接面向用户，
 * 逐状态码显式映射错误（401 → key 无效、429 → 限流、5xx → 服务端错误……），
 * 便于 UI 给出可操作的提示。CancellationException 必须原样上抛（用户点取消 ≠ 网络错误）。
 *
 * 流式为 SSE（text/event-stream）：按行读取（OkHttp 缓冲可容忍 TCP 分块截断单行 JSON），
 * 逐行解析、坏行静默跳过；无 [DONE] 哨兵的网关以流结束为完成信号。
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
        val httpRequest = buildHttpRequest(request, stream = false)
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(httpRequest).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        parseSuccess(bodyText, request.protocol)
                    } else {
                        httpFailure(response.code, bodyText)
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

    override fun stream(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        val httpRequest = buildHttpRequest(request, stream = true)
        val call = client.newCall(httpRequest)
        // 用户取消时中止阻塞中的 socket 读，避免线程挂到 readTimeout
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    val bodyText = body?.string().orEmpty()
                    val failure = httpFailure(response.code, bodyText)
                    emit(AiStreamEvent.Failed(failure.kind, failure.detail))
                    return@flow
                }
                var lastModel: String? = null
                while (true) {
                    val line = body.source().readUtf8Line() ?: break
                    if (!line.startsWith(SSE_DATA_PREFIX)) continue
                    val chunk = AiChatParser.parseSseData(
                        line.removePrefix(SSE_DATA_PREFIX),
                        request.protocol,
                    ) ?: continue
                    chunk.error?.let { message ->
                        emit(AiStreamEvent.Failed(AiFailureKind.BAD_REQUEST, message))
                        return@flow
                    }
                    if (chunk.model != null && chunk.model != lastModel) {
                        lastModel = chunk.model
                        emit(AiStreamEvent.Model(chunk.model))
                    }
                    if (!chunk.delta.isNullOrEmpty()) emit(AiStreamEvent.Delta(chunk.delta))
                }
                emit(AiStreamEvent.Completed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            emit(AiStreamEvent.Failed(AiFailureKind.TIMEOUT, e.message))
        } catch (e: IOException) {
            emit(AiStreamEvent.Failed(AiFailureKind.NETWORK, e.message))
        }
    }.flowOn(Dispatchers.IO)

    /** 组装两种协议的请求体与 HTTP 请求；[stream] 决定 SSE 开关。 */
    private fun buildHttpRequest(request: AiChatRequest, stream: Boolean): Request {
        val baseUrl = request.baseUrl.trim().trimEnd('/')
        val url = baseUrl + when (request.protocol) {
            AiApiProtocol.CHAT_COMPLETIONS -> chatCompletionsSuffix
            AiApiProtocol.RESPONSES -> responsesSuffix
        }

        val payload = buildJsonObject {
            put("model", request.model)
            when (request.protocol) {
                AiApiProtocol.CHAT_COMPLETIONS -> {
                    put(
                        "messages",
                        json.encodeToJsonElement(ListSerializer(AiChatMessage.serializer()), request.messages),
                    )
                    put("max_tokens", request.maxTokens)
                    put("temperature", request.temperature)
                }
                AiApiProtocol.RESPONSES -> {
                    // Responses 协议：system 进 instructions，用户消息为 input
                    put(
                        "instructions",
                        request.messages.firstOrNull { it.role == "system" }?.content.orEmpty(),
                    )
                    put("input", request.messages.filter { it.role != "system" }.joinToString("\n\n") { it.content })
                    // 混合推理模型（如腾讯 hy3）的思考 token 也计入 max_output_tokens：
                    // 实测思考常占 90%+ 预算，3072 会导致正文被截断甚至无正文（status=incomplete）。
                    put("max_output_tokens", request.maxTokens * 2)
                }
            }
            put("stream", stream)
        }.toString()

        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /** 逐状态码映射非 2xx 响应：服务商错误说明（中文优先）作为 detail。 */
    private fun httpFailure(code: Int, bodyText: String): AiChatResult.Failure {
        val detail = AiChatParser.errorBody(bodyText, preferChineseError)
        val kind = when {
            code == 401 || code == 403 -> AiFailureKind.UNAUTHORIZED
            code == 429 -> AiFailureKind.RATE_LIMITED
            code in 500..599 -> AiFailureKind.SERVER_ERROR
            // 400 等：服务商的错误说明（模型名不存在 / 余额不足）对用户最有用
            else -> AiFailureKind.BAD_REQUEST
        }
        return AiChatResult.Failure(
            kind,
            detail ?: if (kind == AiFailureKind.BAD_REQUEST) bodyText.take(300) else null,
        )
    }

    private fun parseSuccess(bodyText: String, protocol: AiApiProtocol): AiChatResult {
        val parsed = when (protocol) {
            AiApiProtocol.CHAT_COMPLETIONS -> AiChatParser.parse(bodyText)
            AiApiProtocol.RESPONSES -> AiChatParser.parseResponses(bodyText)
        } ?: return AiChatResult.Failure(AiFailureKind.EMPTY_RESPONSE)
        return AiChatResult.Success(text = parsed.first, model = parsed.second, promptTokens = null, completionTokens = null)
    }

    private companion object {
        const val SSE_DATA_PREFIX = "data:"
    }
}
