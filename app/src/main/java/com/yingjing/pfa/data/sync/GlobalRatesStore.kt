package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.yingjing.pfa.domain.model.FxRates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.globalRatesDataStore by preferencesDataStore(name = "global_rates")

/**
 * 存储「上次同步」时的汇率快照，用于计算汇率跨周期涨跌%（国际提醒）。
 *
 * 与 [FxRepositoryImpl] 不同：这里只保存上一次的值做差值，不参与货币换算；
 * 首次同步时两 key 均缺失 → [prevFx] 返回 null，该次不生成汇率类国际提醒，但会在同步末尾被 seed。
 */
@Singleton
class GlobalRatesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val snapshot = context.globalRatesDataStore.data.map { prefs ->
        val usd = prefs[PREV_USD_CNY]
        val hkd = prefs[PREV_HKD_CNY]
        if (usd == null && hkd == null) null else FxRates(usd ?: 1.0, hkd ?: 1.0)
    }

    /** 上一次同步保存的汇率；从未保存过返回 null。 */
    suspend fun prevFx(): FxRates? = snapshot.first()

    /** 把本次抓取到的汇率保存为「上次值」，供下一次同步做差值。 */
    suspend fun savePrevFx(rates: FxRates) {
        context.globalRatesDataStore.edit {
            it[PREV_USD_CNY] = rates.usdToCny
            it[PREV_HKD_CNY] = rates.hkdToCny
        }
    }

    private companion object {
        val PREV_USD_CNY = doublePreferencesKey("prev_usd_cny")
        val PREV_HKD_CNY = doublePreferencesKey("prev_hkd_cny")
    }
}
