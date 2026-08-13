package com.yingjing.pfa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FxRatesTest {

    private val rates = FxRates(usdToCny = 7.0, hkdToCny = 0.9)

    @Test
    fun toCny_fromUsd() {
        assertEquals(700.0, rates.toCny(100.0, Currency.USD), 0.001)
    }

    @Test
    fun toCny_fromCny_isIdentity() {
        assertEquals(100.0, rates.toCny(100.0, Currency.CNY), 0.001)
    }

    @Test
    fun fromCny_toHkd() {
        assertEquals(100.0, rates.fromCny(90.0, Currency.HKD), 0.001)
    }

    @Test
    fun convert_usdToHkd_viaCny() {
        // 100 USD -> 700 CNY -> 700/0.9 HKD
        assertEquals(700.0 / 0.9, rates.convert(100.0, Currency.USD, Currency.HKD), 0.001)
    }

    @Test
    fun convert_sameCurrency_isIdentity() {
        assertEquals(100.0, rates.convert(100.0, Currency.USD, Currency.USD), 0.001)
    }

    @Test
    fun defaultRates_doNotCrash_onZero() {
        assertEquals(100.0, FxRates().convert(100.0, Currency.USD, Currency.CNY), 0.001)
    }
}
