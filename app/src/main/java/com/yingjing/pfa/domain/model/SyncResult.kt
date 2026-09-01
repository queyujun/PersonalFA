package com.yingjing.pfa.domain.model

/**
 * 同步过程中可独立成败的数据源。UI 用 [SyncSource.labelRes] 映射为展示名。
 *
 * 一个源「失败」= 该源抓取超时/异常/返回空结果（有持仓或本应产出数据却为空）。
 * 仅在「本应有数据却没拿到」时计为失败，避免无持仓时报一堆无意义失败。
 */
enum class SyncSource {
    FX,
    MARKET_INDEX,
    COMMODITY,
    IPO,
    HOUSE_PRICE,
    STOCK,
    CRYPTO,
    FUND;

    /** UI 展示名 resId；用 exhaustive when，新增源必须显式补文案。 */
    val labelRes: Int
        get() = when (this) {
            FX -> com.yingjing.pfa.R.string.sync_src_fx
            MARKET_INDEX -> com.yingjing.pfa.R.string.sync_src_market_index
            COMMODITY -> com.yingjing.pfa.R.string.sync_src_commodity
            IPO -> com.yingjing.pfa.R.string.sync_src_ipo
            HOUSE_PRICE -> com.yingjing.pfa.R.string.sync_src_house_price
            STOCK -> com.yingjing.pfa.R.string.sync_src_stock
            CRYPTO -> com.yingjing.pfa.R.string.sync_src_crypto
            FUND -> com.yingjing.pfa.R.string.sync_src_fund
        }
}

/**
 * 房价指数同步状态备注。让用户在同步弹窗里看到房价指数「已更新至 X 月 / 已是最新（X 月）」，
 * 而非只在失败时才得知其状态（REFRESHED/SKIPPED 都不进 failedSources，原先对用户完全静默）。
 *
 * @property status REFRESHED=本次拉到新数据；SKIPPED=新鲜度窗口内跳过、用缓存。
 * @property latestMonth 最新报告月份 "yyyy-MM"（可能为 null：无持仓房产或缓存为空）。
 */
data class HousePriceSyncNote(
    val status: HousePriceSyncStatus,
    val latestMonth: String?,
)

enum class HousePriceSyncStatus { REFRESHED, SKIPPED }

/**
 * 一次同步的整体结果。经 [com.yingjing.pfa.data.sync.SyncStateStore] 持久化后上报 UI。
 *
 * @property success 全部数据源均成功更新（failedSources 为空）。
 * @property failedSources 失败的数据源列表（按枚举自然序，UI 用于逐项列出）。
 * @property completedAt 完成时刻（epoch ms）。
 * @property manual 是否由用户手动触发（手动触发时即便全成功也弹窗确认，自动定时仅在失败时弹窗）。
 * @property housePriceNote 房价指数状态备注（无持仓房产时为 null）。让用户显式看到指数变化/新鲜度。
 */
data class SyncResult(
    val success: Boolean,
    val failedSources: List<SyncSource>,
    val completedAt: Long,
    val manual: Boolean,
    val housePriceNote: HousePriceSyncNote? = null,
) {
    /** 是否有部分源失败（含全失败）。 */
    val hasFailure: Boolean get() = failedSources.isNotEmpty()
}

/**
 * 行情抓取（持仓现价）的细分结果：价格映射 + 失败的数据源。
 * 由 [com.yingjing.pfa.data.repository.QuoteRepositoryImpl] 在并行抓取各段后汇总。
 *
 * @property prices holdingId → 现价（以持仓自身币种计）。
 * @property failedSources 失败的源（有对应持仓却抓空 / 抛异常）。
 */
data class QuoteFetchResult(
    val prices: Map<Long, Double>,
    val failedSources: List<SyncSource>,
)
