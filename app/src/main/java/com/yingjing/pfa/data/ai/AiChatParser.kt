package com.yingjing.pfa.data.ai

import kotlinx.serialization.json.Json
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
}
