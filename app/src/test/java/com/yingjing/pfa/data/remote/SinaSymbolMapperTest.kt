package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SinaSymbolMapperTest {

    private fun holding(type: AssetType, symbol: String?) =
        Holding(userId = 1, type = type, name = "x", currency = Currency.CNY, symbol = symbol)

    @Test
    fun aShare_shanghai_prefix() {
        assertEquals("sh600519", SinaSymbolMapper.sinaCode(holding(AssetType.A_SHARE, "600519")))
    }

    @Test
    fun aShare_shenzhen_prefix() {
        assertEquals("sz000001", SinaSymbolMapper.sinaCode(holding(AssetType.A_SHARE, "000001")))
    }

    @Test
    fun goldEtf_asAShareCode() {
        assertEquals("sh518880", SinaSymbolMapper.sinaCode(holding(AssetType.GOLD_ETF, "518880")))
    }

    @Test
    fun hkStock_padsAndPrefixes() {
        assertEquals("rt_hk00700", SinaSymbolMapper.sinaCode(holding(AssetType.HK_STOCK, "700")))
    }

    @Test
    fun usStock_lowercasePrefix() {
        assertEquals("gb_aapl", SinaSymbolMapper.sinaCode(holding(AssetType.US_STOCK, "AAPL")))
    }

    @Test
    fun crypto_returnsNull() {
        assertNull(SinaSymbolMapper.sinaCode(holding(AssetType.CRYPTO, "bitcoin")))
    }

    @Test
    fun blankSymbol_returnsNull() {
        assertNull(SinaSymbolMapper.sinaCode(holding(AssetType.A_SHARE, null)))
    }
}
