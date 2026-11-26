package com.yingjing.pfa.domain.model

import androidx.annotation.StringRes
import com.yingjing.pfa.R

/**
 * 市场情绪页的一条日线数据点：[epochDay] 为交易日（`LocalDate.toEpochDay()`，美股按美东日期、
 * 加密按 UTC 日期），[value] 为当日收盘值（波动率指数点位 / 恐惧贪婪 0–100 / 价格）。
 */
data class SeriesPoint(
    val epochDay: Long,
    val value: Double,
)

/** 恐慌指数的判读方式。 */
enum class FearScale {
    /** 波动率指数：越高越恐慌，无固定阈值 → 用历史分位判读。 */
    VOLATILITY,

    /** 恐惧贪婪 0–100：越低越恐慌，固定五档。 */
    FEAR_GREED,
}

/** 恐慌指数所属分组（总览页按组展示，顺序即展示顺序）。 */
enum class FearGroup(@StringRes val titleRes: Int) {
    EQUITY(R.string.fear_group_equity),
    CRYPTO(R.string.fear_group_crypto),
    COMMODITY(R.string.fear_group_commodity),
}

/**
 * 叠加在恐慌指数走势上的「相关价格」序列，用于观察情绪与价格的相关性。
 *
 * @property code 稳定存储键（写入 Room，不随枚举重命名变化）。
 * @property decimals 价格展示小数位。
 */
enum class PriceSeriesId(
    val code: String,
    @StringRes val labelRes: Int,
    val decimals: Int,
) {
    SPX("px_spx", R.string.price_spx, 0),
    NDX("px_ndx", R.string.price_ndx, 0),
    GLD("px_gld", R.string.price_gld, 2),
    USO("px_uso", R.string.price_uso, 2),
    BTC("px_btc", R.string.price_btc, 0),
}

/**
 * 详情页叠加序列：通常是相关价格；VVIX 叠加 VIX 本身（「恐慌的恐慌」对照恐慌）。
 *
 * @property code 叠加序列的存储键。
 * @property decimals 叠加值展示小数位。
 */
data class OverlaySpec(
    val code: String,
    @StringRes val labelRes: Int,
    val decimals: Int,
) {
    companion object {
        fun of(price: PriceSeriesId) = OverlaySpec(price.code, price.labelRes, price.decimals)
    }
}

/**
 * 关注的恐慌指数（顺序即总览页展示顺序）。
 *
 * @property code 稳定存储键（写入 Room，亦作导航参数）。
 * @property sinceYear 历史数据起始年份（详情页「全历史」文案）。
 */
enum class FearIndexId(
    val code: String,
    val scale: FearScale,
    val group: FearGroup,
    @StringRes val nameRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val explainRes: Int,
) {
    VIX("fx_vix", FearScale.VOLATILITY, FearGroup.EQUITY, R.string.fear_vix_name, R.string.fear_vix_subtitle, R.string.fear_vix_explain),
    VXN("fx_vxn", FearScale.VOLATILITY, FearGroup.EQUITY, R.string.fear_vxn_name, R.string.fear_vxn_subtitle, R.string.fear_vxn_explain),
    VVIX("fx_vvix", FearScale.VOLATILITY, FearGroup.EQUITY, R.string.fear_vvix_name, R.string.fear_vvix_subtitle, R.string.fear_vvix_explain),
    CRYPTO_FNG("fx_crypto_fng", FearScale.FEAR_GREED, FearGroup.CRYPTO, R.string.fear_fng_name, R.string.fear_fng_subtitle, R.string.fear_fng_explain),
    GVZ("fx_gvz", FearScale.VOLATILITY, FearGroup.COMMODITY, R.string.fear_gvz_name, R.string.fear_gvz_subtitle, R.string.fear_gvz_explain),
    OVX("fx_ovx", FearScale.VOLATILITY, FearGroup.COMMODITY, R.string.fear_ovx_name, R.string.fear_ovx_subtitle, R.string.fear_ovx_explain),
    ;

    /** 指数值展示小数位（恐惧贪婪为整数）。 */
    val decimals: Int
        get() = if (scale == FearScale.FEAR_GREED) 0 else 2

    /** 详情页叠加序列（getter 求值，避免枚举初始化顺序问题）。 */
    val overlay: OverlaySpec
        get() = when (this) {
            VIX -> OverlaySpec.of(PriceSeriesId.SPX)
            VXN -> OverlaySpec.of(PriceSeriesId.NDX)
            VVIX -> OverlaySpec(VIX.code, VIX.nameRes, VIX.decimals)
            CRYPTO_FNG -> OverlaySpec.of(PriceSeriesId.BTC)
            GVZ -> OverlaySpec.of(PriceSeriesId.GLD)
            OVX -> OverlaySpec.of(PriceSeriesId.USO)
        }

    companion object {
        fun fromCode(code: String?): FearIndexId? = entries.firstOrNull { it.code == code }
    }
}
