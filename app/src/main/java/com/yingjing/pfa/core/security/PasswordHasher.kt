package com.yingjing.pfa.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PBKDF2-HMAC-SHA256 密码哈希。
 *
 * 存储格式：`pbkdf2:<iterations>:<saltB64>:<hashB64>`。纯 JVM 实现，单元测试可直接验证。
 */
@Singleton
class PasswordHasher @Inject constructor() {

    fun hash(password: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password.toCharArray(), salt, iterations, KEY_BYTES)
        val encoder = Base64.getEncoder()
        return "$PREFIX:$iterations:${encoder.encodeToString(salt)}:${encoder.encodeToString(hash)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        val actual = pbkdf2(password.toCharArray(), salt, iterations, expected.size)
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBytes * 8)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }

    private companion object {
        const val PREFIX = "pbkdf2"
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val KEY_BYTES = 32
    }
}
