package com.yingjing.pfa.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun encryptThenDecrypt_roundTrips() {
        val plain = "盈景私助 backup 数据 123".toByteArray()
        val blob = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        val out = BackupCrypto.decrypt(blob, "correct horse".toCharArray())
        assertArrayEquals(plain, out)
    }

    @Test
    fun decrypt_wrongPassphrase_returnsNull() {
        val blob = BackupCrypto.encrypt("secret".toByteArray(), "right".toCharArray())
        assertNull(BackupCrypto.decrypt(blob, "wrong".toCharArray()))
    }

    @Test
    fun decrypt_corruptOrForeign_returnsNull() {
        assertNull(BackupCrypto.decrypt("not a backup".toByteArray(), "x".toCharArray()))
        assertNull(BackupCrypto.decrypt(ByteArray(3), "x".toCharArray()))
    }

    @Test
    fun encrypt_producesDifferentCiphertextEachTime() {
        val a = BackupCrypto.encrypt("same".toByteArray(), "pw".toCharArray())
        val b = BackupCrypto.encrypt("same".toByteArray(), "pw".toCharArray())
        // 随机盐/IV → 密文不同，但都能解开
        assert(!a.contentEquals(b))
        assertArrayEquals("same".toByteArray(), BackupCrypto.decrypt(a, "pw".toCharArray()))
        assertArrayEquals("same".toByteArray(), BackupCrypto.decrypt(b, "pw".toCharArray()))
    }
}
