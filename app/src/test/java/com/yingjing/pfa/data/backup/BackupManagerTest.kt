package com.yingjing.pfa.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.data.local.HoldingEntity
import com.yingjing.pfa.data.local.UserEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        manager = BackupManager(db.userDao(), db.holdingDao(), db.netWorthSnapshotDao(), db.categorySnapshotDao(), db.alertDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun holding(id: Long, userId: Long, name: String) = HoldingEntity(
        id = id, userId = userId, type = "A_SHARE", name = name, currency = "CNY",
        quantity = 100.0, costPrice = 1650.0, currentPrice = 1680.0, symbol = "600519",
        manualValue = null, city = null, areaSqm = null, annualRatePercent = null,
        startDateEpochMs = null, maturityDateEpochMs = null, depositType = null, sharePercent = null,
        liabilityType = null, monthlyPayment = null, createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun export_thenWipe_thenImport_restoresData() = runTest {
        db.userDao().insert(UserEntity(id = 1, username = "alex", passwordHash = "h", defaultCurrency = "CNY", createdAt = 1))
        db.holdingDao().insertAll(listOf(holding(10, 1, "茅台"), holding(11, 1, "宁德")))

        val blob = manager.export("pw123".toCharArray())

        // 清空
        db.holdingDao().deleteAll()
        db.userDao().deleteAll()
        assertEquals(0, db.userDao().count())

        val ok = manager.import(blob, "pw123".toCharArray())
        assertTrue(ok)
        assertEquals(1, db.userDao().count())
        assertEquals("alex", db.userDao().findById(1)!!.username)
        assertEquals(2, db.holdingDao().getByUser(1).size)
        assertEquals("茅台", db.holdingDao().findById(10)!!.name)
    }

    @Test
    fun import_wrongPassphrase_returnsFalse_andKeepsData() = runTest {
        db.userDao().insert(UserEntity(id = 1, username = "alex", passwordHash = "h", defaultCurrency = "CNY", createdAt = 1))
        val blob = manager.export("right".toCharArray())
        val ok = manager.import(blob, "wrong".toCharArray())
        assertFalse(ok)
        assertEquals(1, db.userDao().count()) // 未被清空
    }
}
