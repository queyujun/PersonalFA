package com.yingjing.pfa.data.repository

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.yingjing.pfa.data.remote.FxRemote
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.repository.FxRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.fxDataStore by preferencesDataStore(name = "fx_rates")

@Singleton
class FxRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fxRemote: FxRemote,
) : FxRepository {

    override fun observeRates(): Flow<FxRates> = context.fxDataStore.data.map { prefs ->
        FxRates(
            usdToCny = prefs[USD_CNY] ?: 1.0,
            hkdToCny = prefs[HKD_CNY] ?: 1.0,
        )
    }

    override suspend fun current(): FxRates = observeRates().first()

    override suspend fun refresh(): FxRates {
        val rates = fxRemote.fetch()
        context.fxDataStore.edit {
            it[USD_CNY] = rates.usdToCny
            it[HKD_CNY] = rates.hkdToCny
        }
        return rates
    }

    private companion object {
        val USD_CNY = doublePreferencesKey("usd_cny")
        val HKD_CNY = doublePreferencesKey("hkd_cny")
    }
}
