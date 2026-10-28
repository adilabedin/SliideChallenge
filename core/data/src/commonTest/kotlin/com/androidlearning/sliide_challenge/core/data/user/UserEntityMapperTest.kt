package com.androidlearning.sliide_challenge.core.data.user

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.domain.user.User
import kotlin.test.Test

class UserEntityMapperTest {

    @Test
    fun `user survives a round trip through the entity`() {
        val user = User(1, "Ada", "ada@x.io")

        assertThat(user.toEntity().toDomain()).isEqualTo(user)
    }
}
