package com.yingjing.pfa.domain.alert

import com.yingjing.pfa.fakes.FakeStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpoAlertsTest {

    private val resolver = FakeStringResolver()
    private val now = 1_700_000_000_000L

    private val ipos = listOf(
        IpoItem("马矿股份", "780123", "2026-08-21"),
        IpoItem("瑞康股份", "301999", "2026-08-14"),
        IpoItem("恒达科技", "787001", "2026-08-14"),
    )

    @Test
    fun todaysIpos_singleAlert_withCount() {
        val alerts = AlertRules.ipoAlerts(
            userId = 1, ipos = ipos, todayDate = "2026-08-14", nowMs = now, resolver = resolver,
        )
        assertEquals(1, alerts.size)
        // 标题含今日申购数量（2 只）；正文串接各新股名 + 申购代码。
        assertTrue(alerts[0].title.contains("2"))
        assertTrue(alerts[0].body.contains("瑞康股份"))
        assertTrue(alerts[0].body.contains("301999"))
        assertEquals("ipo_2026-08-14", alerts[0].dedupKey)
    }

    @Test
    fun noIpoToday_noAlert() {
        assertTrue(AlertRules.ipoAlerts(1, ipos, "2026-08-15", now, resolver).isEmpty())
    }
}
