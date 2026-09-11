package com.yingjing.pfa.data.backup

import com.yingjing.pfa.core.backup.BackupCrypto
import com.yingjing.pfa.data.ai.AiApiProtocol
import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.data.ai.AiReportTone
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
        val profiles = aiSettingsStore.profiles.first()
        val data = BackupData(
            users = userDao.getAll().map { it.toBackup() },
            holdings = holdingDao.getAllForBackup().map { it.toBackup() },
            snapshots = snapshotDao.getAllForBackup().map { it.toBackup() },
            categorySnapshots = categorySnapshotDao.getAllForBackup().map { it.toBackup() },
            alerts = alertDao.getAllForBackup().map { it.toBackup() },
            subscriptions = subscriptionDao.getAllForBackup().map { it.toBackup() },
            aiRecords = aiRecordDao.getAllForBackup().map { it.toBackup() },
            aiProfiles = profiles.map { it.toBackup() },
            aiActiveProfileId = aiSettingsStore.activeProfileId.first(),
            aiConsented = aiSettingsStore.consented.first(),
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
        restoreAiConfig(data)
        // 汇率写回本地缓存（老备份无此字段 → null → 不写回，靠后台刷新补救）。
        data.fxRates?.let { fxRepository.save(it.toDomain()) }
        return true
    }

    /**
     * AI 配置写回（API Key 不在备份内，恢复后须重录）：
     * - 新备份（aiProfiles 非空）：整体替换档案列表 + 生效 id + 全局同意；
     * - 老备份（仅 aiSettings）：映射为对应预设档案（custom → custom_legacy）并设为生效；
     * - 两者皆无 → 不动本机配置。
     */
    private suspend fun restoreAiConfig(data: BackupData) {
        if (data.aiProfiles.isNotEmpty()) {
            // 整体替换：先清掉本机已有自定义档案（预设 6 个 id 固定，直接覆盖）。
            aiSettingsStore.profiles.first()
                .filter { it.isPreset.not() }
                .forEach { aiSettingsStore.deleteProfile(it.id) }
            data.aiProfiles.forEach { aiSettingsStore.saveProfile(it.toDomain()) }
            aiSettingsStore.setActiveProfile(
                data.aiActiveProfileId?.takeIf { id -> data.aiProfiles.any { it.id == id } },
            )
            data.aiConsented?.let { aiSettingsStore.setConsented(it) }
            return
        }
        val legacy = data.aiSettings ?: return
        val providerId = AiProviderPreset.fromId(legacy.providerId).id
        val profileId = if (providerId == AiProviderPreset.CUSTOM.id) {
            LEGACY_CUSTOM_PROFILE_ID
        } else {
            AiProfile.presetIdOf(AiProviderPreset.fromId(providerId))
        }
        val profile = AiProfile(
            id = profileId,
            providerId = providerId,
            baseUrl = legacy.baseUrl,
            model = legacy.model,
            protocol = AiApiProtocol.fromId(legacy.protocol),
            includeDetails = legacy.includeDetails,
        )
        aiSettingsStore.saveProfile(profile)
        aiSettingsStore.setActiveProfile(profileId)
        aiSettingsStore.setConsented(legacy.consented)
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

private fun AiProfile.toBackup() = BackupAiProfile(
    id = id,
    name = name,
    providerId = providerId,
    baseUrl = baseUrl,
    model = model,
    protocol = protocol.id,
    includeDetails = includeDetails,
    tone = tone.id,
    maxTokens = maxTokens,
)

private fun BackupAiProfile.toDomain() = AiProfile(
    id = id,
    name = name,
    providerId = providerId,
    baseUrl = baseUrl,
    model = model,
    protocol = AiApiProtocol.fromId(protocol),
    includeDetails = includeDetails,
    tone = AiReportTone.fromId(tone),
    maxTokens = maxTokens,
)

/** 老备份 aiSettings（providerId=custom）映射的档案 id，与 DataStore 迁移保持一致。 */
private const val LEGACY_CUSTOM_PROFILE_ID = "custom_legacy"
