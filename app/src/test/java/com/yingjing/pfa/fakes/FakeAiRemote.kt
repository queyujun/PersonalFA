package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.ai.AiChatRequest
import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.data.ai.AiRemote

/** 内存版 AI 远程：记录最近请求，按预设脚本返回成功/失败，供 ViewModel 测试。 */
class FakeAiRemote : AiRemote {

    /** 队列：每次 complete 取一个；为空时返回默认成功。 */
    val results = ArrayDeque<AiChatResult>()

    val requests = mutableListOf<AiChatRequest>()

    override suspend fun complete(request: AiChatRequest): AiChatResult {
        requests += request
        return results.removeFirstOrNull() ?: AiChatResult.Success(
            text = "ok",
            model = request.model,
            promptTokens = null,
            completionTokens = null,
        )
    }
}
