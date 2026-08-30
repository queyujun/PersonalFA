package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.sync.LanguageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 内存版 LanguageStore，纯 JVM 可跑；[setLanguage] 同步更新便于断言。 */
class FakeLanguageStore(initial: String? = null) : LanguageStore {
    private val _tag = MutableStateFlow(initial)
    override val languageTag: Flow<String?> = _tag.asStateFlow()
    val tag: String? get() = _tag.value
    override suspend fun setLanguage(tag: String?) { _tag.value = tag }
}
