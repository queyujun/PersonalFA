package com.yingjing.pfa.data.local

import androidx.room.Entity

/**
 * 市场情绪页日线缓存（恐慌指数 + 叠加价格）。复合主键 (seriesCode, epochDay) 支持 upsert。
 *
 * 属公开行情缓存：不进备份（恢复后可重新下载），可随时整表重建。
 * [seriesCode] 取 [com.yingjing.pfa.domain.model.FearIndexId.code] /
 * [com.yingjing.pfa.domain.model.PriceSeriesId.code]（稳定字符串，不依赖枚举名）。
 */
@Entity(tableName = "market_series_points", primaryKeys = ["seriesCode", "epochDay"])
data class MarketSeriesPointEntity(
    val seriesCode: String,
    val epochDay: Long,
    val value: Double,
)
