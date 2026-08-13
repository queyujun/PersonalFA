package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.fakes.FakeHoldingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddHoldingUseCaseTest {

    private val repository = FakeHoldingRepository()
    private val addHolding = AddHoldingUseCase(repository)

    @Test
    fun invalidHolding_returnsInvalid() = runTest {
        val result = addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "", currency = Currency.CNY),
        )
        assertTrue(result is SaveHoldingResult.Invalid)
    }

    @Test
    fun validHolding_returnsSuccess_andPersists() = runTest {
        val result = addHolding(
            Holding(
                userId = 1, type = AssetType.A_SHARE, name = "贵州茅台",
                currency = Currency.CNY, symbol = "600519", quantity = 100.0, costPrice = 1650.0,
            ),
        )
        assertTrue(result is SaveHoldingResult.Success)
        assertEquals(1, repository.observeHoldings(1).first().size)
    }
}
