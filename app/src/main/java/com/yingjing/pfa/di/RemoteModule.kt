package com.yingjing.pfa.di

import com.yingjing.pfa.data.remote.CoinGeckoRemote
import com.yingjing.pfa.data.remote.CommodityRemote
import com.yingjing.pfa.data.remote.CryptoQuoteRemote
import com.yingjing.pfa.data.remote.EastmoneyFundNavRemote
import com.yingjing.pfa.data.remote.EastmoneyHousePriceRemote
import com.yingjing.pfa.data.remote.EastmoneyIpoRemote
import com.yingjing.pfa.data.remote.FxRemote
import com.yingjing.pfa.data.remote.FundQuoteRemote
import com.yingjing.pfa.data.remote.HousePriceRemote
import com.yingjing.pfa.data.remote.IpoRemote
import com.yingjing.pfa.data.remote.MarketIndexRemote
import com.yingjing.pfa.data.remote.SinaFxRemote
import com.yingjing.pfa.data.remote.SinaCommodityRemote
import com.yingjing.pfa.data.remote.SinaMarketIndexRemote
import com.yingjing.pfa.data.remote.SinaStockRemote
import com.yingjing.pfa.data.remote.StockQuoteRemote
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {
    @Binds
    @Singleton
    abstract fun bindStockQuoteRemote(impl: SinaStockRemote): StockQuoteRemote

    @Binds
    @Singleton
    abstract fun bindCryptoQuoteRemote(impl: CoinGeckoRemote): CryptoQuoteRemote

    @Binds
    @Singleton
    abstract fun bindFxRemote(impl: SinaFxRemote): FxRemote

    @Binds
    @Singleton
    abstract fun bindMarketIndexRemote(impl: SinaMarketIndexRemote): MarketIndexRemote

    @Binds
    @Singleton
    abstract fun bindCommodityRemote(impl: SinaCommodityRemote): CommodityRemote

    @Binds
    @Singleton
    abstract fun bindIpoRemote(impl: EastmoneyIpoRemote): IpoRemote

    @Binds
    @Singleton
    abstract fun bindHousePriceRemote(impl: EastmoneyHousePriceRemote): HousePriceRemote

    @Binds
    @Singleton
    abstract fun bindFundQuoteRemote(impl: EastmoneyFundNavRemote): FundQuoteRemote
}
