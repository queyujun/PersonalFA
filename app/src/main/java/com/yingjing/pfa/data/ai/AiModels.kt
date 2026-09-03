package com.yingjing.pfa.data.ai

/**
 * AI 功能的模型与请求参数（OpenAI 兼容 chat completions 协议）。
 */

import kotlinx.serialization.Serializable

/**
 * 服务商预设：选中后回填默认 Base URL 与模型名，用户仍可手改。
 * [defaultBaseUrl] / [defaultModel] 为空表示自定义（用户必填）。
 */
enum class AiProviderPreset(
    val id: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    DEEPSEEK("deepseek", "https://api.deepseek.com/v1", "deepseek-chat"),
    OPENAI("openai", "https://api.openai.com/v1", "gpt-4o-mini"),
    KIMI("kimi", "https://api.moonshot.cn/v1", "moonshot-v1-32k"),
    QWEN("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    HUNYUAN(
        "hunyuan",
        "https://api.hunyuan.cloud.tencent.com/v1",
        "hunyuan-turbos-latest",
    ),
    DOUBAO(
        "doubao",
        "https://ark.cn-beijing.volces.com/api/v3",
        // 豆包无固定模型名：model 须填方舟控制台复制的模型 ID 或接入点 ep-xxx，预设只回填 base URL。
        "",
    ),
    CUSTOM("custom", "", "");

    companion object {
        /** 按 id 反查（DataStore 存的是 id 字符串）；未知 id 回退 DEEPSEEK。 */
        fun fromId(id: String?): AiProviderPreset =
            entries.firstOrNull { it.id == id } ?: DEEPSEEK
    }
}

/**
 * AI 配置。API Key 不在此存储（走 [com.yingjing.pfa.core.security.AiSecretStore]），
 * 这里只存「是否已配置」由外部单独查询。
 */
data class AiSettings(
    val providerId: String = AiProviderPreset.DEEPSEEK.id,
    val baseUrl: String = "",
    val model: String = "",
    /** payload 是否包含明细持仓（false = 仅分类汇总，进一步降低外发数据量）。 */
    val includeDetails: Boolean = true,
    /** 用户已确认隐私提示（资产数据将发送到所配置服务商）。 */
    val consented: Boolean = false,
) {
    val provider: AiProviderPreset get() = AiProviderPreset.fromId(providerId)

    /** baseUrl 与模型均已填写即视为已配置（key 是否存在单独查询）。 */
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
}

/** 单条对话消息（role: "system" / "user"）。 */
@Serializable
data class AiChatMessage(
    val role: String,
    val content: String,
)

/** 一次完整补全请求（已含密钥与端点信息，remote 层直接映射为 HTTP）。 */
data class AiChatRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val messages: List<AiChatMessage>,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val temperature: Double = DEFAULT_TEMPERATURE,
) {
    companion object {
        const val DEFAULT_MAX_TOKENS = 3072
        const val DEFAULT_TEMPERATURE = 0.3
    }
}
