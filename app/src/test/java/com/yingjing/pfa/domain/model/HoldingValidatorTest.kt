package com.yingjing.pfa.domain.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HoldingValidatorTest {

    private fun holding(type: AssetType, name: String = "标的", block: Holding.() -> Holding = { this }) =
        Holding(userId = 1, type = type, name = name, currency = Currency.CNY).block()

    @Test
    fun blankName_isInvalid() {
        assertNotNull(HoldingValidator.validate(holding(AssetType.REAL_ESTATE, name = "") { copy(manualValue = 1.0) }))
    }

    @Test
    fun stock_missingSymbol_isInvalid() {
        assertNotNull(HoldingValidator.validate(holding(AssetType.A_SHARE) { copy(quantity = 100.0) }))
    }

    @Test
    fun stock_missingQuantity_isInvalid() {
        assertNotNull(HoldingValidator.validate(holding(AssetType.A_SHARE) { copy(symbol = "600519") }))
    }

    @Test
    fun stock_valid_isNull() {
        assertNull(HoldingValidator.validate(holding(AssetType.A_SHARE) { copy(symbol = "600519", quantity = 100.0) }))
    }

    @Test
    fun deposit_missingRate_isInvalid() {
        assertNotNull(HoldingValidator.validate(holding(AssetType.DEPOSIT) { copy(manualValue = 100.0) }))
    }

    @Test
    fun deposit_valid_isNull() {
        assertNull(HoldingValidator.validate(holding(AssetType.DEPOSIT) { copy(manualValue = 100.0, annualRatePercent = 2.0) }))
    }

    @Test
    fun liability_missingValue_isInvalid() {
        assertNotNull(HoldingValidator.validate(holding(AssetType.LIABILITY)))
    }

    @Test
    fun realEstate_valid_isNull() {
        assertNull(HoldingValidator.validate(holding(AssetType.REAL_ESTATE) { copy(manualValue = 1_750_000.0) }))
    }
}
