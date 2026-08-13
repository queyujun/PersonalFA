package com.yingjing.pfa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HoldingRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: HoldingRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = HoldingRepositoryImpl(db.holdingDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun stock(userId: Long = 1) = Holding(
        userId = userId, type = AssetType.A_SHARE, name = "贵州茅台",
        currency = Currency.CNY, symbol = "600519", quantity = 100.0, costPrice = 1650.0,
    )

    @Test
    fun add_thenObserve_emitsHolding() = runTest {
        val id = repository.addHolding(stock())
        val list = repository.observeHoldings(1).first()
        assertEquals(1, list.size)
        assertEquals(id, list[0].id)
    }

    @Test
    fun roundTrip_typeAndCurrency_preserved() = runTest {
        val id = repository.addHolding(
            Holding(
                userId = 1, type = AssetType.US_STOCK, name = "Apple",
                currency = Currency.USD, symbol = "AAPL", quantity = 50.0, costPrice = 200.0,
            ),
        )
        val holding = repository.getHolding(id)!!
        assertEquals(AssetType.US_STOCK, holding.type)
        assertEquals(Currency.USD, holding.currency)
    }

    @Test
    fun addHolding_setsTimestamps() = runTest {
        val id = repository.addHolding(stock())
        assertTrue(repository.getHolding(id)!!.createdAtEpochMs > 0)
    }

    @Test
    fun updateHolding_persistsChange() = runTest {
        val id = repository.addHolding(stock())
        val holding = repository.getHolding(id)!!
        repository.updateHolding(holding.copy(currentPrice = 1680.0))
        assertEquals(1680.0, repository.getHolding(id)!!.currentPrice)
    }

    @Test
    fun deleteHolding_removes() = runTest {
        val id = repository.addHolding(stock())
        repository.deleteHolding(id)
        assertNull(repository.getHolding(id))
    }
}
