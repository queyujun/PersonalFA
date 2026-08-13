package com.yingjing.pfa.core.validation

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialValidatorTest {

    @Test
    fun username_empty_isInvalid() = assertNotNull(CredentialValidator.validateUsername(""))

    @Test
    fun username_tooShort_isInvalid() = assertNotNull(CredentialValidator.validateUsername("a"))

    @Test
    fun username_tooLong_isInvalid() =
        assertNotNull(CredentialValidator.validateUsername("x".repeat(21)))

    @Test
    fun username_withIllegalChar_isInvalid() =
        assertNotNull(CredentialValidator.validateUsername("bad name!"))

    @Test
    fun username_letters_isValid() = assertNull(CredentialValidator.validateUsername("alex_01"))

    @Test
    fun username_chinese_isValid() = assertNull(CredentialValidator.validateUsername("小明"))

    @Test
    fun password_empty_isInvalid() = assertNotNull(CredentialValidator.validatePassword(""))

    @Test
    fun password_tooShort_isInvalid() = assertNotNull(CredentialValidator.validatePassword("123"))

    @Test
    fun password_valid_isNull() = assertNull(CredentialValidator.validatePassword("password1"))
}
