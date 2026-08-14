package com.yingjing.pfa.domain.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpoAlertsTest {

    private val now = 1_700_000_000_000L

    private val ipos = listOf(
        IpoItem("马矿股份", "780123", "2026-08-21"),
        IpoItem("瑞康股份", "301999", "2026-08-14"),
        IpoItem("恒达科技", "787001", "2026-08-14"),
    )

    @Test
    fun todaysIpos_singleAlert_withCount() {
        val alerts = AlertRules.ipoAlerts(userId = 1, ipos = ipos, todayDate = "2026-08-14", nowMs = now)
        assertEquals(1, alerts.size)
        assertTrue(alerts[0].title.contains("2 只"))
        assertTrue(alerts[0].body.contains("瑞康股份"))
        assertEquals("ipo_2026-08-14", alerts[0].dedupKey)
    }

    @Test
    fun noIpoToday_noAlert() {
        assertTrue(AlertRules.ipoAlerts(1, ipos, "2026-08-15", now).isEmpty())
    }
}
