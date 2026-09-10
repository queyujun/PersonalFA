package com.yingjing.pfa.data.ai

/**
 * AI 功能的模型与请求参数（OpenAI 兼容：Chat Completions / Responses 双协议）。
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
 * 接口协议：同一 OpenAI 兼容 Base URL 下，服务商可能只支持其中一种。
 * - CHAT_COMPLETIONS：POST {base}/chat/completions，请求 `messages`，主流通用；
 * - RESPONSES：POST {base}/responses，请求 `instructions` + `input`（腾讯 TokenHub hy3 即只开此路）。
 */
enum class AiApiProtocol(val id: String) {
    CHAT_COMPLETIONS("chat_completions"),
    RESPONSES("responses"),
    ;

    companion object {
        /** 按 id 反查（DataStore 存的是 id 字符串）；未知 id 回退 CHAT_COMPLETIONS。 */
        fun fromId(id: String?): AiApiProtocol =
            entries.firstOrNull { it.id == id } ?: CHAT_COMPLETIONS
    }
}

/**
 * AI 输出语气档：分析师（默认，专业评判口吻）/ 伙伴（叙事化陪伴口吻，安静的老派管家）。
 * 只改提示词模板，不影响数据外发范围。
 */
enum class AiReportTone(val id: String) {
    ANALYST("analyst"),
    COMPANION("companion"),
    ;

    companion object {
        /** 按 id 反查（DataStore/备份存的是 id 字符串）；未知 id 回退 ANALYST。 */
        fun fromId(id: String?): AiReportTone =
            entries.firstOrNull { it.id == id } ?: ANALYST
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
    val protocol: AiApiProtocol = AiApiProtocol.CHAT_COMPLETIONS,
    /** payload 是否包含明细持仓（false = 仅分类汇总，进一步降低外发数据量）。 */
    val includeDetails: Boolean = true,
    /** 报告/分析输出语气档。 */
    val tone: AiReportTone = AiReportTone.ANALYST,
    /** 用户已确认隐私提示（资产数据将发送到所配置服务商）。 */
    val consented: Boolean = false,
) {
    val provider: AiProviderPreset get() = AiProviderPreset.fromId(providerId)

    /** baseUrl 与模型均已填写即视为已配置（key 是否存在单独查询）。 */
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
}

/**
 * AI 配置档案。预设服务商各固定一个（id = `preset_<providerId>`），自定义档案可多个
 * （id = `custom_<uuid>`）。API Key 不在此存储——按 [id] 走
 * [com.yingjing.pfa.core.security.AiSecretStore] 的档案行。
 */
@Serializable
data class AiProfile(
    val id: String,
    /** 自定义档案的显示名；预设档案留空（UI 用服务商资源名）。 */
    val name: String = "",
    /** 服务商预设 id：预设档案 = 自身；自定义档案 = 选中作模板的预设或 "custom"。 */
    val providerId: String,
    val baseUrl: String = "",
    val model: String = "",
    val protocol: AiApiProtocol = AiApiProtocol.CHAT_COMPLETIONS,
    /** payload 是否包含明细持仓（false = 仅分类汇总，进一步降低外发数据量）。 */
    val includeDetails: Boolean = true,
    /** 报告/分析输出语气档（旧 JSON/旧备份无此字段 → 默认 ANALYST，向后兼容）。 */
    val tone: AiReportTone = AiReportTone.ANALYST,
) {
    val isPreset: Boolean get() = id.startsWith(PRESET_ID_PREFIX)
    val provider: AiProviderPreset get() = AiProviderPreset.fromId(providerId)

    /** baseUrl 与模型均已填写即视为已配置（key 是否存在单独查询）。 */
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        const val PRESET_ID_PREFIX = "preset_"

        /** 预设档案 id（每个服务商固定一个）。 */
        fun presetIdOf(provider: AiProviderPreset): String = PRESET_ID_PREFIX + provider.id

        /** 新自定义档案 id（UUID 保证唯一，删除后重建不会撞旧档案）。 */
        fun newCustomId(): String = "custom_" + java.util.UUID.randomUUID().toString()
    }
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
    val protocol: AiApiProtocol = AiApiProtocol.CHAT_COMPLETIONS,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val temperature: Double = DEFAULT_TEMPERATURE,
) {
    companion object {
        const val DEFAULT_MAX_TOKENS = 3072
        const val DEFAULT_TEMPERATURE = 0.3
    }
}
