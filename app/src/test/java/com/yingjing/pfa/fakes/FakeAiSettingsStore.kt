package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.ai.AiSettings
import com.yingjing.pfa.data.ai.AiSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 内存版 AI 配置存储；key 明文保存在内存字段（模拟 Keystore 层），供 ViewModel 测试。 */
class FakeAiSettingsStore : AiSettingsStore {

    private val _settings = MutableStateFlow(AiSettings())
    override val settings: Flow<AiSettings> = _settings

    var storedKey: String? = null
        private set

    override suspend fun save(settings: AiSettings) {
        _settings.value = settings
    }

    override suspend fun setApiKey(raw: String?) {
        storedKey = raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun hasApiKey(): Boolean = storedKey != null

    override suspend fun apiKey(): String? = storedKey
}

/** 内存版密钥存储（模拟 Keystore 行为：写入/读取/清除/失效返回 null）。 */
class FakeAiSecretStore : com.yingjing.pfa.core.security.AiSecretStore {

    private var value: String? = null

    var failReads: Boolean = false

    override fun exists(): Boolean = value != null

    override fun read(): String? = if (failReads) null else value

    override fun write(value: String) {
        this.value = value
    }

    override fun clear() {
        value = null
    }
}
