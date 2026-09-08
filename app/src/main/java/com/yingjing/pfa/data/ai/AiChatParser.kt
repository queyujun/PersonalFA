package com.yingjing.pfa.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
/**
 * OpenAI 兼容 chat completions 响应体的解析（纯函数，便于单元测试）。
 *
 * 兼容各家的细微差异：只在能取到 content 时返回成功；模型名缺失返回 null。
 * [sanitize] 处理 DeepSeek 等推理模型会把思考过程包在 `<think>` 围栏里的情况。
 */
object AiChatParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 解析成功响应：返回 (content, model)；结构不符合预期（空 choices 等）返回 null。
     */
    fun parse(body: String): Pair<String, String?>? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val content = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content ?: return null
        if (content.isBlank()) return null
        val model = root["model"]?.jsonPrimitive?.takeIf { it.isString }?.content
        content to model
    }.getOrNull()

    /**
     * 解析 OpenAI Responses API 成功响应：返回 (text, model)；取不到文本返回 null。
     *
     * 响应结构为 `output: [{type:"message", content:[{type:"output_text", text:"..."}]}]`；
     * 兼容个别网关直接给 `output_text` 顶层字段的情况。
     */
    fun parseResponses(body: String): Pair<String, String?>? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val model = root["model"]?.jsonPrimitive?.takeIf { it.isString }?.content
        val text = root["output_text"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: root["output"]?.jsonArray
                ?.asSequence()
                ?.filterIsInstance<kotlinx.serialization.json.JsonObject>()
                ?.filter { it["type"]?.jsonPrimitive?.content != "reasoning" }
                ?.flatMap { it["content"]?.jsonArray?.asSequence().orEmpty() }
                ?.filterIsInstance<kotlinx.serialization.json.JsonObject>()
                ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "output_text" }
                ?.get("text")?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return null
        if (text.isBlank()) return null
        text to model
    }.getOrNull()

    /**
     * 解析错误响应体中的服务商错误说明；取不到返回 null。
     *
     * 各家通用格式为 `error.message`；腾讯 TokenHub 等网关同时返回
     * `error.message_zh`（中文说明）——中文环境优先展示它。
     */
    fun errorBody(body: String, preferChinese: Boolean = false): String? = runCatching {
        val error = json.parseToJsonElement(body).jsonObject["error"]?.jsonObject ?: return null
        val zh = if (preferChinese) error["message_zh"]?.jsonPrimitive?.takeIf { it.isString }?.content else null
        (zh?.takeIf { it.isNotBlank() } ?: error["message"]?.jsonPrimitive?.takeIf { it.isString }?.content)
    }.getOrNull()

    /**
     * 剥离推理模型输出的 `<think>...</think>` 围栏（含未闭合的前缀），
     * 并去掉首尾空白。
     */
    fun sanitize(text: String): String {
        val withoutThink = THINK_FENCED.replace(text, "")
            .let { stripped -> THINK_UNCLOSED.find(stripped)?.let { m -> stripped.removeRange(m.range) } ?: stripped }
        return withoutThink.trim()
    }

    // 已闭合：<think>…</think>（dotall 跨行）
    private val THINK_FENCED = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
    // 未闭合：开头 <think> 之后直到结尾（响应被截断时出现）
    private val THINK_UNCLOSED = Regex("^\\s*<think>.*$", RegexOption.DOT_MATCHES_ALL)

    // ---- 流式（SSE）解析 ----

    /**
     * 解析流式（SSE）单个 `data:` 载荷，返回增量与模型名；与正文无关的事件返回 null。
     *
     * 兼容两种协议：
     * - Chat Completions：`choices[0].delta.content` 为正文增量，`delta.reasoning_content`
     *   为推理内容（须剔除），每块顶层带 `model`；
     * - Responses：按事件 `type` 分派，仅 `response.output_text.delta` 取 `delta` 字段，
     *   `response.created` / `response.in_progress` 取 `response.model`；推理增量与
     *   `*.done` 收尾事件（携带全文，重复追加会导致内容翻倍）一律忽略。
     *
     * TCP 分块可能截断单行 JSON：解析失败的行静默跳过（返回 null），不断流。
     */
    fun parseSseData(data: String, protocol: AiApiProtocol): AiSseChunk? = runCatching {
        val payload = data.trim()
        if (payload.isEmpty() || payload == DONE_SENTINEL) return@runCatching null
        val root = json.parseToJsonElement(payload).jsonObject
        // 部分网关以流内 error 事件（而非 HTTP 状态码）表达失败
        (root["error"] as? JsonObject)?.let { error ->
            val message = error.stringAt("message_zh") ?: error.stringAt("message")
            return@runCatching AiSseChunk(delta = null, model = null, error = message ?: payload.take(300))
        }
        when (protocol) {
            AiApiProtocol.CHAT_COMPLETIONS -> parseChatChunk(root)
            AiApiProtocol.RESPONSES -> parseResponsesChunk(root)
        }
    }.getOrNull()

    private fun parseChatChunk(root: JsonObject): AiSseChunk? {
        val model = root.stringAt("model")
        val delta = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject?.stringAt("content")
        return if (delta == null && model == null) null else AiSseChunk(delta, model, null)
    }

    private fun parseResponsesChunk(root: JsonObject): AiSseChunk? = when (root.stringAt("type")) {
        "response.output_text.delta" -> root.stringAt("delta")
            ?.takeIf { it.isNotEmpty() }
            ?.let { AiSseChunk(delta = it, model = null, error = null) }
        "response.created", "response.in_progress" ->
            (root["response"] as? JsonObject)?.stringAt("model")
                ?.let { AiSseChunk(delta = null, model = it, error = null) }
        else -> null
    }

    /** 仅取字符串类型的字段值（显式 JSON null 不得当作 "null" 文本）。 */
    private fun JsonObject.stringAt(key: String): String? =
        when (val value = this[key]) {
            is JsonNull -> null
            is JsonPrimitive -> value.takeIf { it.isString }?.content
            else -> null
        }

    private const val DONE_SENTINEL = "[DONE]"
}

/** SSE 单事件解析结果：[delta] 正文增量、[model] 模型名、[error] 流中错误说明。 */
data class AiSseChunk(
    val delta: String?,
    val model: String?,
    val error: String?,
)
