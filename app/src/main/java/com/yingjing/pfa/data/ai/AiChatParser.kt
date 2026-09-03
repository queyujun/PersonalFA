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

    /** 解析错误响应体中的 `error.message`（各家通用格式）；取不到返回 null。 */
    fun errorBody(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.content
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
