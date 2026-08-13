package com.yingjing.pfa.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    private val hasher = PasswordHasher()

    @Test
    fun hash_isNotPlaintext_andVerifies() {
        val stored = hasher.hash("secret123", iterations = 1000)
        assertNotEquals("secret123", stored)
        assertTrue(hasher.verify("secret123", stored))
    }

    @Test
    fun verify_failsForWrongPassword() {
        val stored = hasher.hash("secret123", iterations = 1000)
        assertFalse(hasher.verify("wrongpass", stored))
    }

    @Test
    fun hash_usesRandomSalt_soSamePasswordDiffers() {
        val a = hasher.hash("samePassword", iterations = 1000)
        val b = hasher.hash("samePassword", iterations = 1000)
        assertNotEquals(a, b)
        assertTrue(hasher.verify("samePassword", a))
        assertTrue(hasher.verify("samePassword", b))
    }

    @Test
    fun verify_rejectsMalformedStoredValue() {
        assertFalse(hasher.verify("x", "garbage"))
        assertFalse(hasher.verify("x", ""))
        assertFalse(hasher.verify("x", "pbkdf2:notanumber:a:b"))
    }

    @Test
    fun storedFormat_hasFourColonParts() {
        val stored = hasher.hash("pw", iterations = 1000)
        assertEquals(4, stored.split(":").size)
        assertTrue(stored.startsWith("pbkdf2:1000:"))
    }
}
