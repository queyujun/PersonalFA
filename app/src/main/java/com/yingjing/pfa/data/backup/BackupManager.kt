package com.yingjing.pfa.data.backup

import com.yingjing.pfa.core.backup.BackupCrypto
import com.yingjing.pfa.data.ai.AiSettings
import com.yingjing.pfa.data.ai.AiSettingsStore
import com.yingjing.pfa.data.local.AiReportRecordDao
import com.yingjing.pfa.data.local.AiReportRecordEntity
import com.yingjing.pfa.data.local.AlertDao
import com.yingjing.pfa.data.local.AlertEntity
import com.yingjing.pfa.data.local.CategorySnapshotDao
import com.yingjing.pfa.data.local.CategorySnapshotEntity
import com.yingjing.pfa.data.local.HoldingDao
import com.yingjing.pfa.data.local.HoldingEntity
import com.yingjing.pfa.data.local.NetWorthSnapshotDao
import com.yingjing.pfa.data.local.NetWorthSnapshotEntity
import com.yingjing.pfa.data.local.SubscriptionDao
import com.yingjing.pfa.data.local.SubscriptionEntity
import com.yingjing.pfa.data.local.UserDao
import com.yingjing.pfa.data.local.UserEntity
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.repository.FxRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 全量备份导出 / 恢复（加密）。恢复会清空现有数据并按原 ID 还原。 */
@Singleton
class BackupManager @Inject constructor(
    private val userDao: UserDao,
    private val holdingDao: HoldingDao,
    private val snapshotDao: NetWorthSnapshotDao,
    private val categorySnapshotDao: CategorySnapshotDao,
    private val alertDao: AlertDao,
    private val subscriptionDao: SubscriptionDao,
    private val aiRecordDao: AiReportRecordDao,
    private val aiSettingsStore: AiSettingsStore,
    private val fxRepository: FxRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 导出为加密字节。 */
    suspend fun export(passphrase: CharArray): ByteArray {
        val data = BackupData(
            users = userDao.getAll().map { it.toBackup() },
            holdings = holdingDao.getAllForBackup().map { it.toBackup() },
            snapshots = snapshotDao.getAllForBackup().map { it.toBackup() },
            categorySnapshots = categorySnapshotDao.getAllForBackup().map { it.toBackup() },
            alerts = alertDao.getAllForBackup().map { it.toBackup() },
            subscriptions = subscriptionDao.getAllForBackup().map { it.toBackup() },
            aiRecords = aiRecordDao.getAllForBackup().map { it.toBackup() },
            aiSettings = aiSettingsStore.settings.first().toBackup(),
            fxRates = fxRepository.current().toBackup(),
        )
        return BackupCrypto.encrypt(json.encodeToString(data).toByteArray(Charsets.UTF_8), passphrase)
    }

    /** 从加密字节恢复；口令错误或格式非法返回 false。 */
    suspend fun import(blob: ByteArray, passphrase: CharArray): Boolean {
        val plain = BackupCrypto.decrypt(blob, passphrase) ?: return false
        val data = runCatching { json.decodeFromString<BackupData>(String(plain, Charsets.UTF_8)) }
            .getOrNull() ?: return false

        alertDao.deleteAll()
        categorySnapshotDao.deleteAll()
        snapshotDao.deleteAll()
        holdingDao.deleteAll()
        subscriptionDao.deleteAll()
        aiRecordDao.deleteAll()
        userDao.deleteAll()

        userDao.insertAll(data.users.map { it.toEntity() })
        holdingDao.insertAll(data.holdings.map { it.toEntity() })
        snapshotDao.insertAll(data.snapshots.map { it.toEntity() })
        categorySnapshotDao.insertAll(data.categorySnapshots.map { it.toEntity() })
        alertDao.insertAll(data.alerts.map { it.toEntity() })
        subscriptionDao.insertAll(data.subscriptions.map { it.toEntity() })
        aiRecordDao.insertAll(data.aiRecords.map { it.toEntity() })
        // AI 配置写回（老备份无此字段 → null → 不动本机配置）；API Key 不在备份内，恢复后须重录。
        data.aiSettings?.let { aiSettingsStore.save(it.toDomain()) }
        // 汇率写回本地缓存（老备份无此字段 → null → 不写回，靠后台刷新补救）。
        data.fxRates?.let { fxRepository.save(it.toDomain()) }
        return true
    }
}

private fun UserEntity.toBackup() =
    BackupUser(id, username, passwordHash, defaultCurrency, createdAt, nickname, gender, age)

private fun BackupUser.toEntity() =
    UserEntity(id, username, passwordHash, defaultCurrency, createdAt, nickname, gender, age)

private fun HoldingEntity.toBackup() = BackupHolding(
    id, userId, type, name, currency, quantity, costPrice, currentPrice, symbol, manualValue,
    city, areaSqm, annualRatePercent, startDateEpochMs, maturityDateEpochMs, depositType,
    sharePercent, liabilityType, monthlyPayment, repaymentDay, lastRepaidYearMonth,
    autoEstimate, valueBaseDateEpochMs, estimatedValue, note, autoFetchNav, createdAt, updatedAt,
)

private fun BackupHolding.toEntity() = HoldingEntity(
    id, userId, type, name, currency, quantity, costPrice, currentPrice, symbol, manualValue,
    city, areaSqm, annualRatePercent, startDateEpochMs, maturityDateEpochMs, depositType,
    sharePercent, liabilityType, monthlyPayment, repaymentDay, lastRepaidYearMonth,
    autoEstimate, valueBaseDateEpochMs, estimatedValue, note, autoFetchNav, createdAt, updatedAt,
)

private fun NetWorthSnapshotEntity.toBackup() =
    BackupSnapshot(id, userId, dayEpochDay, currency, totalAssets, totalLiabilities, netWorth, createdAt)

private fun BackupSnapshot.toEntity() =
    NetWorthSnapshotEntity(id, userId, dayEpochDay, currency, totalAssets, totalLiabilities, netWorth, createdAt)

private fun CategorySnapshotEntity.toBackup() =
    BackupCategorySnapshot(id, userId, dayEpochDay, category, amount)

private fun BackupCategorySnapshot.toEntity() =
    CategorySnapshotEntity(id, userId, dayEpochDay, category, amount)

private fun AlertEntity.toBackup() =
    BackupAlert(id, userId, category, severity, title, body, refHoldingId, dedupKey, createdAt, read)

private fun BackupAlert.toEntity() =
    AlertEntity(id, userId, category, severity, title, body, refHoldingId, dedupKey, createdAt, read)

private fun SubscriptionEntity.toBackup() = BackupSubscription(
    id, userId, name, category, note, currency, amount, cycle,
    firstBillEpochMs, nextRenewalEpochMs, reminderDaysBefore, paymentMethod, active, createdAt, updatedAt,
)

private fun BackupSubscription.toEntity() = SubscriptionEntity(
    id, userId, name, category, note, currency, amount, cycle,
    firstBill, nextRenewal, reminderDaysBefore, paymentMethod, active, createdAt, updatedAt,
)

private fun FxRates.toBackup() = BackupFxRates(usdToCny = usdToCny, hkdToCny = hkdToCny)

private fun BackupFxRates.toDomain() = FxRates(usdToCny = usdToCny, hkdToCny = hkdToCny)

private fun AiReportRecordEntity.toBackup() = BackupAiRecord(
    id, userId, kind, title, model, markdown, createdAt,
)

private fun BackupAiRecord.toEntity() = AiReportRecordEntity(
    id = id, userId = userId, kind = kind, title = title, model = model,
    markdown = markdown, createdAt = createdAt,
)

private fun AiSettings.toBackup() = BackupAiSettings(
    providerId = providerId,
    baseUrl = baseUrl,
    model = model,
    protocol = protocol.id,
    includeDetails = includeDetails,
    consented = consented,
)

private fun BackupAiSettings.toDomain() = AiSettings(
    providerId = providerId,
    baseUrl = baseUrl,
    model = model,
    protocol = com.yingjing.pfa.data.ai.AiApiProtocol.fromId(protocol),
    includeDetails = includeDetails,
    consented = consented,
)
