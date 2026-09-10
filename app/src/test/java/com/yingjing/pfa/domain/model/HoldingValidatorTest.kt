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

    @Test
    fun realEstate_autoEstimate_nonListedCity_isInvalid() {
        // 苏州是特大城市但不在国家统计局 70 城名单（江苏仅南京/无锡/徐州/扬州），须拦截
        assertNotNull(
            HoldingValidator.validate(
                holding(AssetType.REAL_ESTATE) { copy(manualValue = 1_750_000.0, city = "苏州", autoEstimate = true) },
            ),
        )
    }

    @Test
    fun realEstate_autoEstimate_nullCity_isInvalid() {
        assertNotNull(
            HoldingValidator.validate(
                holding(AssetType.REAL_ESTATE) { copy(manualValue = 1_750_000.0, city = null, autoEstimate = true) },
            ),
        )
    }

    @Test
    fun realEstate_autoEstimate_listedCity_isNull() {
        assertNull(
            HoldingValidator.validate(
                holding(AssetType.REAL_ESTATE) { copy(manualValue = 1_750_000.0, city = "北京", autoEstimate = true) },
            ),
        )
    }

    @Test
    fun realEstate_noEstimate_anyCity_isNull() {
        // 不开估算时城市自由填写（如国外城市）不影响保存
        assertNull(
            HoldingValidator.validate(
                holding(AssetType.REAL_ESTATE) { copy(manualValue = 1_750_000.0, city = "苏州", autoEstimate = false) },
            ),
        )
    }

    @Test
    fun otcFund_missingSymbol_isInvalid() {
        assertNotNull(HoldingValidator.validate(holding(AssetType.OTC_FUND) { copy(quantity = 100.0) }))
    }

    @Test
    fun otcFund_missingQuantity_isInvalid() {
        assertNotNull(HoldingValidator.validate(holding(AssetType.OTC_FUND) { copy(symbol = "005827") }))
    }

    @Test
    fun otcFund_valid_isNull() {
        assertNull(HoldingValidator.validate(holding(AssetType.OTC_FUND) { copy(symbol = "005827", quantity = 1_000.0) }))
    }
}
