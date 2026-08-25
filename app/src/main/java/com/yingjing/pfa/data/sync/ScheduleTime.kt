package com.yingjing.pfa.data.sync

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 周期任务的首次延迟计算（纯函数，可单测）。 */
object ScheduleTime {

    /**
     * 从 [nowMs] 到下一个「[hour]:00」的延迟毫秒；若今天该整点已到/已过，则顺延到明天。
     * [hour] 会被裁剪到 0..23。
     */
    fun initialDelayMillis(nowMs: Long, hour: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val h = hour.coerceIn(0, 23)
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        var next = now.withHour(h).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
