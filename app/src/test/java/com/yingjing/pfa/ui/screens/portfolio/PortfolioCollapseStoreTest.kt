package com.yingjing.pfa.ui.screens.portfolio

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioCollapseStoreTest {

    @Test
    fun initiallyAllCollapsed() = runTest {
        val store = PortfolioCollapseStore()
        store.expandedCategories.test {
            assertTrue(awaitItem().isEmpty()) // 空集 = 全折叠
            cancelAndIgnoreRemainingEvents()
        }
        store.expandedSubGroups.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleCategory_addsThenRemoves() = runTest {
        val store = PortfolioCollapseStore()
        store.toggleCategory("STOCK")
        assertEquals(setOf("STOCK"), store.expandedCategories.value)
        store.toggleCategory("STOCK")
        assertTrue(store.expandedCategories.value.isEmpty())
    }

    @Test
    fun toggleCategory_doesNotMutateOtherKeys() = runTest {
        val store = PortfolioCollapseStore()
        store.toggleCategory("STOCK")
        store.toggleCategory("DEPOSIT")
        assertEquals(setOf("STOCK", "DEPOSIT"), store.expandedCategories.value)
        store.toggleCategory("STOCK")
        assertEquals(setOf("DEPOSIT"), store.expandedCategories.value)
    }

    @Test
    fun toggleSubGroup_addsThenRemoves() = runTest {
        val store = PortfolioCollapseStore()
        val key = "STOCK|A股"
        store.toggleSubGroup(key)
        assertEquals(setOf(key), store.expandedSubGroups.value)
        store.toggleSubGroup(key)
        assertTrue(store.expandedSubGroups.value.isEmpty())
    }

    @Test
    fun toggleCategory_emitsImmutableSnapshot() = runTest {
        val store = PortfolioCollapseStore()
        store.expandedCategories.test {
            assertTrue(awaitItem().isEmpty())
            store.toggleCategory("STOCK")
            assertEquals(setOf("STOCK"), awaitItem())
            store.toggleCategory("DEPOSIT")
            assertEquals(setOf("STOCK", "DEPOSIT"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
