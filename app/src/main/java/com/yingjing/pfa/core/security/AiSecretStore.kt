package com.yingjing.pfa.core.security

/**
 * AI 功能密钥（OpenAI 兼容服务的 API Key）的安全存取，按配置档案隔离。
 *
 * 密钥随普通数据存入 Room 数据库（[com.yingjing.pfa.data.local.AppMetaDao] 键值表），
 * 入库前用应用层 AES/GCM 加密、读取时解密——即使导出数据库文件也拿不到明文。
 *
 * 约束：
 * - 绝不进备份（BackupManager 不导出 app_meta 表）、绝不进日志、绝不进 prompt；
 * - 加密密钥由数据库口令派生，生命周期与数据库对齐：数据库能打开，密钥就能解密。
 */
interface AiSecretStore {
    /** 指定档案是否已保存过密钥（数据库有行即算）。 */
    suspend fun exists(profileId: String): Boolean

    /** 读取并解密指定档案的 API Key；不存在或解密失败（数据损坏等）返回 null。 */
    suspend fun read(profileId: String): String?

    /** 加密并保存指定档案的 API Key。 */
    suspend fun write(profileId: String, value: String)

    /** 清除指定档案已保存的密钥。 */
    suspend fun clear(profileId: String)

    /**
     * 一次性迁移：把旧版全局单 key 行（多档案改造前）划归指定档案。
     * 幂等——目标档案已有密钥或旧行不存在时均为空操作。
     */
    suspend fun migrateLegacySingleKeyTo(profileId: String)
}
