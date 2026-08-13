package com.yingjing.pfa.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetWorthSnapshotDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NetWorthSnapshotDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.netWorthSnapshotDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snapshot(day: Long, net: Double) = NetWorthSnapshotEntity(
        userId = 1, dayEpochDay = day, currency = "CNY",
        totalAssets = net, totalLiabilities = 0.0, netWorth = net, createdAt = day,
    )

    @Test
    fun upsert_and_observe() = runTest {
        dao.upsert(snapshot(100, 1000.0))
        dao.upsert(snapshot(101, 1100.0))
        val list = dao.observeByUser(1).first()
        assertEquals(2, list.size)
        assertEquals(1000.0, list[0].netWorth, 0.001) // 按天升序
    }

    @Test
    fun sameDay_isReplaced_notDuplicated() = runTest {
        dao.upsert(snapshot(100, 1000.0))
        dao.upsert(snapshot(100, 1234.0)) // 同一天覆盖
        val list = dao.observeByUser(1).first()
        assertEquals(1, list.size)
        assertEquals(1234.0, list[0].netWorth, 0.001)
    }
}
