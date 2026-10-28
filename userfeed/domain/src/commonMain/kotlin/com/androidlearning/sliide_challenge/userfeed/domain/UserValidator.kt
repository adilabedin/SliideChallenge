package com.androidlearning.sliide_challenge.userfeed.domain

import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Error
import com.androidlearning.sliide_challenge.core.domain.util.Result

enum class NameValidationError : Error {
    EMPTY,
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
}

enum class EmailValidationError : Error {
    EMPTY,
    TOO_LONG,
    INVALID_FORMAT,
}

/**
 * Rules for the name and email of a user being created or edited. Both values are expected to be normalised first
 * ([normalizeName], [normalizeEmail]); the same normalised values are what get saved.
 */
object UserValidator {
    const val NAME_MIN_LENGTH = 2
    const val NAME_MAX_LENGTH = 100

    /** RFC 5321 limits: 254 characters for the whole address, 64 for the part before the `@`. */
    const val EMAIL_MAX_LENGTH = 254
    private const val EMAIL_LOCAL_PART_MAX_LENGTH = 64

    /**
     * Starts with a letter in any script, then letters, combining marks (accents written as separate code points),
     * spaces, apostrophes (straight or curly), hyphens and full stops: "José Núñez", "O'Brien", "Jean-Luc", "J. R. R.".
     */
    private val NAME_PATTERN = Regex("^\\p{L}[\\p{L}\\p{M} '’.-]*$")

    /**
     * A practical subset of RFC 5322 rather than the full grammar (no quoted local parts or IP-literal domains):
     * - local part: dot-separated runs of the RFC "atext" characters, so no leading, trailing or double dots;
     * - domain: at least two dot-separated labels of letters, digits and inner hyphens, each at most 63 characters;
     * - top-level domain: letters only, at least two of them.
     */
    private val EMAIL_PATTERN = Regex(
        "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
            "@([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$",
    )

    private val WHITESPACE_RUN = Regex("\\s+")

    /** Trims the name and collapses any run of whitespace into a single space. */
    fun normalizeName(name: String): String = name.trim().replace(WHITESPACE_RUN, " ")

    fun normalizeEmail(email: String): String = email.trim()

    fun validateName(name: String): EmptyResult<NameValidationError> {
        val error = when {
            name.isEmpty() -> NameValidationError.EMPTY
            name.length < NAME_MIN_LENGTH -> NameValidationError.TOO_SHORT
            name.length > NAME_MAX_LENGTH -> NameValidationError.TOO_LONG
            !NAME_PATTERN.matches(name) -> NameValidationError.INVALID_CHARACTERS
            else -> return Result.Success(Unit)
        }
        return Result.Error(error)
    }

    fun validateEmail(email: String): EmptyResult<EmailValidationError> {
        val error = when {
            email.isEmpty() -> EmailValidationError.EMPTY
            email.length > EMAIL_MAX_LENGTH -> EmailValidationError.TOO_LONG
            email.substringBefore('@').length > EMAIL_LOCAL_PART_MAX_LENGTH -> EmailValidationError.INVALID_FORMAT
            !EMAIL_PATTERN.matches(email) -> EmailValidationError.INVALID_FORMAT
            else -> return Result.Success(Unit)
        }
        return Result.Error(error)
    }
}
