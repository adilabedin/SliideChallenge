package com.androidlearning.sliide_challenge.userfeed.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.androidlearning.sliide_challenge.core.domain.util.Result
import kotlin.test.Test

class UserValidatorTest {

    @Test
    fun `normalizing a name trims it and collapses inner whitespace`() {
        assertThat(UserValidator.normalizeName("  Ada \t  Lovelace \n")).isEqualTo("Ada Lovelace")
    }

    @Test
    fun `normalizing an email trims it`() {
        assertThat(UserValidator.normalizeEmail("  ada@example.com ")).isEqualTo("ada@example.com")
    }

    @Test
    fun `names in many scripts and with common punctuation are valid`() {
        listOf(
            "Ada Lovelace",
            "José Núñez",
            "Zoë",
            "O'Brien",
            "O’Brien",
            "Jean-Luc Picard",
            "J. R. R. Tolkien",
            "李小龍",
            "Владимир",
            // "e" followed by a combining acute accent rather than the precomposed "é".
            "Renée",
            "Al",
        ).forEach { name ->
            assertThat(UserValidator.validateName(name), name).isInstanceOf<Result.Success<Unit>>()
        }
    }

    @Test
    fun `an empty name is rejected as empty`() {
        assertThat(UserValidator.validateName("")).isEqualTo(Result.Error(NameValidationError.EMPTY))
    }

    @Test
    fun `a one-letter name is too short`() {
        assertThat(UserValidator.validateName("A")).isEqualTo(Result.Error(NameValidationError.TOO_SHORT))
    }

    @Test
    fun `the maximum name length is accepted and one more is too long`() {
        val longest = "a".repeat(UserValidator.NAME_MAX_LENGTH)

        assertThat(UserValidator.validateName(longest)).isInstanceOf<Result.Success<Unit>>()
        assertThat(UserValidator.validateName(longest + "a")).isEqualTo(Result.Error(NameValidationError.TOO_LONG))
    }

    @Test
    fun `names with digits or symbols or leading punctuation are rejected`() {
        listOf("Ada 2", "R2-D2", "Ada!", "ada@example.com", "-Ada", "'Ada", ". Ada", "Ada_Lovelace", "😀 Ada").forEach { name ->
            assertThat(UserValidator.validateName(name), name)
                .isEqualTo(Result.Error(NameValidationError.INVALID_CHARACTERS))
        }
    }

    @Test
    fun `common email shapes are valid`() {
        listOf(
            "ada@example.com",
            "ada.lovelace@example.co.uk",
            "ada+feed@example.com",
            "a_b-c@sub-domain.example.io",
            "ADA@EXAMPLE.COM",
            "o'brien@example.ie",
            "1234@123.example.com",
        ).forEach { email ->
            assertThat(UserValidator.validateEmail(email), email).isInstanceOf<Result.Success<Unit>>()
        }
    }

    @Test
    fun `an empty email is rejected as empty`() {
        assertThat(UserValidator.validateEmail("")).isEqualTo(Result.Error(EmailValidationError.EMPTY))
    }

    @Test
    fun `malformed emails are rejected`() {
        listOf(
            "ada",
            "ada@",
            "@example.com",
            "ada@example",
            "ada@@example.com",
            "ada@example..com",
            "ada@.example.com",
            "ada@example.com.",
            "ada@-example.com",
            "ada@example-.com",
            "ada@example.c",
            "ada@example.c0m",
            ".ada@example.com",
            "ada.@example.com",
            "ada..lovelace@example.com",
            "ada lovelace@example.com",
            "ada@exa mple.com",
            "josé@example.com",
        ).forEach { email ->
            assertThat(UserValidator.validateEmail(email), email)
                .isEqualTo(Result.Error(EmailValidationError.INVALID_FORMAT))
        }
    }

    @Test
    fun `a local part over 64 characters is rejected`() {
        assertThat(UserValidator.validateEmail("a".repeat(64) + "@example.com")).isInstanceOf<Result.Success<Unit>>()
        assertThat(UserValidator.validateEmail("a".repeat(65) + "@example.com"))
            .isEqualTo(Result.Error(EmailValidationError.INVALID_FORMAT))
    }

    @Test
    fun `a domain label over 63 characters is rejected`() {
        assertThat(UserValidator.validateEmail("ada@" + "a".repeat(63) + ".com")).isInstanceOf<Result.Success<Unit>>()
        assertThat(UserValidator.validateEmail("ada@" + "a".repeat(64) + ".com"))
            .isEqualTo(Result.Error(EmailValidationError.INVALID_FORMAT))
    }

    @Test
    fun `an email over 254 characters is too long`() {
        val domain = List(4) { "a".repeat(60) }.joinToString(".") + ".com" // 244 characters
        val atLimit = "a".repeat(UserValidator.EMAIL_MAX_LENGTH - domain.length - 1) + "@" + domain

        assertThat(UserValidator.validateEmail(atLimit)).isInstanceOf<Result.Success<Unit>>()
        assertThat(UserValidator.validateEmail("a$atLimit")).isEqualTo(Result.Error(EmailValidationError.TOO_LONG))
    }
}
