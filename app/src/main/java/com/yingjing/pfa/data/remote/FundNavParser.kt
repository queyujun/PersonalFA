package com.yingjing.pfa.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.regex.Pattern

/**
 * 解析东方财富基金估值接口 fundgz 响应。
 *
 * 响应为 JSONP：`jsonpgz({"fundcode":"005827","dwjz":"2.5432","gsz":"2.5500",...});`
 * - [dwjz] = 最近公布的单位净值（官方，盘后更新）
 * - [gsz]   = 盘中实时估算净值（仅交易时段）
 *
 * 取值优先 [gsz]（更新更及时），为空或非法时回退 [dwjz]；两者皆空则丢弃。
 */
object FundNavParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonp = Pattern.compile("""\{.*}""", Pattern.DOTALL)

    fun parse(body: String): Map<String, Double> {
        val matcher = jsonp.matcher(body)
        if (!matcher.find()) return emptyMap()
        val record = runCatching {
            json.decodeFromString<FundGz>(matcher.group())
        }.getOrNull() ?: return emptyMap()
        val fundCode = record.fundCode ?: return emptyMap()
        // gsz/dwjz 用字符串接收，空串/非数字统一转 null，避免单字段非法导致整条记录解析失败。
        val gsz = record.gsz?.toDoubleOrNull()
        val dwjz = record.dwjz?.toDoubleOrNull()
        val nav = gsz?.takeIf { it > 0.0 }
            ?: dwjz?.takeIf { it > 0.0 }
            ?: return emptyMap()
        return mapOf(fundCode to nav)
    }

    @Serializable
    private data class FundGz(
        @SerialName("fundcode") val fundCode: String? = null,
        @SerialName("dwjz") val dwjz: String? = null,
        @SerialName("gsz") val gsz: String? = null,
    )
}
