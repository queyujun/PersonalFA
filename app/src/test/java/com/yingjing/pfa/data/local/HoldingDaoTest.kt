package com.yingjing.pfa.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HoldingDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: HoldingDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.holdingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(userId: Long = 1, name: String = "茅台") = HoldingEntity(
        userId = userId, type = "A_SHARE", name = name, currency = "CNY",
        quantity = 100.0, costPrice = 1650.0, currentPrice = null, symbol = "600519",
        manualValue = null, city = null, areaSqm = null, annualRatePercent = null,
        startDateEpochMs = null, depositType = null, sharePercent = null,
        liabilityType = null, monthlyPayment = null, createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun insert_and_findById() = runTest {
        val id = dao.insert(entity())
        assertEquals("茅台", dao.findById(id)!!.name)
    }

    @Test
    fun observeByUser_emitsInserted() = runTest {
        dao.insert(entity(userId = 1, name = "a"))
        dao.insert(entity(userId = 1, name = "b"))
        dao.insert(entity(userId = 2, name = "other"))
        val list = dao.observeByUser(1).first()
        assertEquals(2, list.size)
    }

    @Test
    fun update_changesFields() = runTest {
        val id = dao.insert(entity())
        val updated = dao.findById(id)!!.copy(currentPrice = 1680.0)
        dao.update(updated)
        assertEquals(1680.0, dao.findById(id)!!.currentPrice)
    }

    @Test
    fun deleteById_removes() = runTest {
        val id = dao.insert(entity())
        dao.deleteById(id)
        assertNull(dao.findById(id))
    }
}
