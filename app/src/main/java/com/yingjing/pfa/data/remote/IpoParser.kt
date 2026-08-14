package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.alert.IpoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 解析东方财富 RPTA_APP_IPOAPPLY 新股申购日历 JSON。 */
object IpoParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<IpoItem> {
        val response = runCatching { json.decodeFromString<IpoResponse>(body) }.getOrNull()
        val records = response?.result?.data ?: return emptyList()
        return records.mapNotNull { record ->
            val name = record.name ?: return@mapNotNull null
            val code = record.applyCode ?: return@mapNotNull null
            val date = record.applyDate?.substringBefore(" ") ?: return@mapNotNull null
            IpoItem(name = name, applyCode = code, applyDate = date)
        }
    }

    @Serializable
    private data class IpoResponse(val result: IpoResult? = null)

    @Serializable
    private data class IpoResult(val data: List<IpoRecord> = emptyList())

    @Serializable
    private data class IpoRecord(
        @SerialName("SECURITY_NAME") val name: String? = null,
        @SerialName("APPLY_CODE") val applyCode: String? = null,
        @SerialName("APPLY_DATE") val applyDate: String? = null,
    )
}
