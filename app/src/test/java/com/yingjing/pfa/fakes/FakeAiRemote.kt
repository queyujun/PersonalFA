package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.ai.AiChatRequest
import com.yingjing.pfa.data.ai.AiRemote
import com.yingjing.pfa.domain.ai.AiChatResult
import com.yingjing.pfa.domain.ai.AiStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 内存版 AI 远程：记录最近请求，按预设脚本返回成功/失败/流式事件，供 ViewModel 测试。 */
class FakeAiRemote : AiRemote {

    /** 队列：每次 complete 取一个；为空时返回默认成功。 */
    val results = ArrayDeque<AiChatResult>()

    /** 队列：每次 stream 取一组事件；为空时发出默认成功流（两段增量后完成）。 */
    val streams = ArrayDeque<List<AiStreamEvent>>()

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

    override fun stream(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        requests += request
        val events = streams.removeFirstOrNull()
            ?: listOf(
                AiStreamEvent.Model(request.model),
                AiStreamEvent.Delta("ok"),
                AiStreamEvent.Completed,
            )
        events.forEach { emit(it) }
    }
}
