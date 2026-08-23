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
class CategorySnapshotDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CategorySnapshotDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.categorySnapshotDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(day: Long, category: String, amount: Double) =
        CategorySnapshotEntity(userId = 1, dayEpochDay = day, category = category, amount = amount)

    @Test
    fun upsert_and_observe() = runTest {
        dao.upsertAll(listOf(row(100, "STOCK", 1000.0), row(100, "REAL_ESTATE", 5000.0)))
        val list = dao.observeByUser(1).first()
        assertEquals(2, list.size)
    }

    @Test
    fun sameDayAndCategory_isReplaced() = runTest {
        dao.upsertAll(listOf(row(100, "STOCK", 1000.0)))
        dao.upsertAll(listOf(row(100, "STOCK", 1234.0)))
        val list = dao.observeByUser(1).first()
        assertEquals(1, list.size)
        assertEquals(1234.0, list[0].amount, 0.001)
    }

    @Test
    fun differentDays_keptSeparately() = runTest {
        dao.upsertAll(listOf(row(100, "STOCK", 1000.0), row(101, "STOCK", 1100.0)))
        assertEquals(2, dao.observeByUser(1).first().size)
    }
}
