package com.yingjing.pfa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.domain.model.Currency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SnapshotRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = SnapshotRepositoryImpl(db.netWorthSnapshotDao(), db.categorySnapshotDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val dayMs: Long = 100L * 86_400_000L

    @Test
    fun record_thenObserve_categories() = runTest {
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 6000.0, totalLiabilities = 0.0,
            netWorth = 6000.0, categoryAmounts = mapOf("STOCK" to 1000.0, "REAL_ESTATE" to 5000.0), nowMs = dayMs,
        )
        val cats = repository.observeCategories(1).first()
        assertEquals(2, cats.size)
    }

    @Test
    fun record_rewritesSameDay_overwritesOldCategoryValue() = runTest {
        // 第一次：房产 5000，股权 1000
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 6000.0, totalLiabilities = 0.0,
            netWorth = 6000.0, categoryAmounts = mapOf("STOCK" to 1000.0, "REAL_ESTATE" to 5000.0), nowMs = dayMs,
        )
        // 第二次（当天）：房产删光，仅剩股权 1000
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 1000.0, totalLiabilities = 0.0,
            netWorth = 1000.0, categoryAmounts = mapOf("STOCK" to 1000.0), nowMs = dayMs,
        )
        val cats = repository.observeCategories(1).first()
        assertEquals(1, cats.size)
        assertEquals("STOCK", cats[0].category)
        // 关键：房产当天行被清除，不残留旧值 5000
        assertTrue(cats.none { it.category == "REAL_ESTATE" })
    }

    @Test
    fun record_emptyCategoryAmounts_clearsDay() = runTest {
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 6000.0, totalLiabilities = 0.0,
            netWorth = 6000.0, categoryAmounts = mapOf("STOCK" to 1000.0, "REAL_ESTATE" to 5000.0), nowMs = dayMs,
        )
        // 当天所有持仓清空 → categoryAmounts 为空
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 0.0, totalLiabilities = 0.0,
            netWorth = 0.0, categoryAmounts = emptyMap(), nowMs = dayMs,
        )
        val cats = repository.observeCategories(1).first()
        assertTrue(cats.isEmpty())
    }

    @Test
    fun record_rewritesSameDay_clearsAnyCategory_notOnlyRealEstate() = runTest {
        // 通用性回归：修复在快照写入层对所有类别无差别生效。
        // 任一单映射类别（此处用国债 BOND）持仓全删，当天该类别行同样应被清除，不限于房产。
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 800.0, totalLiabilities = 0.0,
            netWorth = 800.0, categoryAmounts = mapOf("BOND" to 800.0), nowMs = dayMs,
        )
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 0.0, totalLiabilities = 0.0,
            netWorth = 0.0, categoryAmounts = emptyMap(), nowMs = dayMs,
        )
        val cats = repository.observeCategories(1).first()
        assertTrue(cats.none { it.category == "BOND" })
        assertTrue(cats.isEmpty())
    }

    @Test
    fun record_differentDays_preserved() = runTest {
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 1000.0, totalLiabilities = 0.0,
            netWorth = 1000.0, categoryAmounts = mapOf("STOCK" to 1000.0), nowMs = dayMs,
        )
        repository.record(
            userId = 1, currency = Currency.CNY, totalAssets = 5000.0, totalLiabilities = 0.0,
            netWorth = 5000.0, categoryAmounts = mapOf("REAL_ESTATE" to 5000.0), nowMs = dayMs + 86_400_000L,
        )
        val cats = repository.observeCategories(1).first()
        assertEquals(2, cats.size)
    }
}
