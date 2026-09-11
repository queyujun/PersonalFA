package com.yingjing.pfa.domain.ai

/**
 * AI 调用结果与失败分类。与项目内其他 remote「吞错返回空值」的契约不同：
 * AI 结果直接面向用户，必须显式区分错误类别，给出可操作的提示。
 */
sealed interface AiChatResult {
    data class Success(
        val text: String,
        val model: String?,
        val promptTokens: Int?,
        val completionTokens: Int?,
    ) : AiChatResult

    data class Failure(
        val kind: AiFailureKind,
        /** 面向日志/调试的原始细节；UI 文案由 kind 映射，不直接展示 detail 以免泄漏。 */
        val detail: String? = null,
    ) : AiChatResult
}

enum class AiFailureKind {
    /** 未配置 baseUrl / 模型。 */
    NOT_CONFIGURED,

    /** 未录入 API Key。 */
    NO_KEY,

    /** 未登录（拿不到用户数据）。 */
    NO_USER,

    /** 网络不可达 / 连接失败。 */
    NETWORK,

    /** 请求超时。 */
    TIMEOUT,

    /** 服务商返回 401/403：key 无效或无权限。 */
    UNAUTHORIZED,

    /** 429：触发限流。 */
    RATE_LIMITED,

    /** 服务商 5xx。 */
    SERVER_ERROR,

    /** 400 等请求错误（如模型名不存在、余额不足等，detail 含服务商说明）。 */
    BAD_REQUEST,

    /** 响应 200 但解析不出内容。 */
    EMPTY_RESPONSE,
}

/**
 * 流式补全事件：按到达顺序发出，以 [Completed]（正常结束）或 [Failed]（失败）终止，
 * 终止事件之后 Flow 即结束。
 */
sealed interface AiStreamEvent {
    /** 正文增量（推理内容已在 remote 层剔除）。 */
    data class Delta(val text: String) : AiStreamEvent

    /** 从响应获知的模型名（重复已去重）。 */
    data class Model(val name: String) : AiStreamEvent

    /** 流正常结束；正文为空时调用方应按 EMPTY_RESPONSE 处理。
     *  [truncated] = 输出因长度限制被截断（调用方应向用户明示，而非静默存为完整报告）。 */
    data class Completed(val truncated: Boolean = false) : AiStreamEvent

    /** 失败终止：HTTP 状态码映射 / 流中 error 事件 / 网络异常。 */
    data class Failed(val kind: AiFailureKind, val detail: String? = null) : AiStreamEvent
}
