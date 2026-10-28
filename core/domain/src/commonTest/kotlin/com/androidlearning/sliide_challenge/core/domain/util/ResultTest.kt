package com.androidlearning.sliide_challenge.core.domain.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class ResultTest {

    private enum class TestError : Error { FAILED }

    private val success: Result<Int, TestError> = Result.Success(2)
    private val failure: Result<Int, TestError> = Result.Error(TestError.FAILED)

    @Test
    fun `map transforms success and passes error through`() {
        assertThat(success.map { it * 10 }).isEqualTo(Result.Success(20))
        assertThat(failure.map { it * 10 }).isEqualTo(Result.Error(TestError.FAILED))
    }

    @Test
    fun `onSuccess runs only for success`() {
        var seen: Int? = null
        failure.onSuccess { seen = it }
        assertThat(seen).isNull()
        success.onSuccess { seen = it }
        assertThat(seen).isEqualTo(2)
    }

    @Test
    fun `onFailure runs only for error`() {
        var seen: TestError? = null
        success.onFailure { seen = it }
        assertThat(seen).isNull()
        failure.onFailure { seen = it }
        assertThat(seen).isEqualTo(TestError.FAILED)
    }

    @Test
    fun `asEmptyResult drops data but keeps error`() {
        assertThat(success.asEmptyResult()).isEqualTo(Result.Success(Unit))
        assertThat(failure.asEmptyResult()).isEqualTo(Result.Error(TestError.FAILED))
    }
}
