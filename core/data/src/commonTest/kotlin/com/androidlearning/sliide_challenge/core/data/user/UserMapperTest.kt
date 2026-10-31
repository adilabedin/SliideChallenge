package com.androidlearning.sliide_challenge.core.data.user

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.domain.user.User
import kotlin.test.Test

class UserMapperTest {

    @Test
    fun `toDomain maps all fields`() {
        val dto = UserDto(
            id = 8686808,
            name = "Rep. Kailash Sinha",
            email = "kailash_rep_sinha@gorczany.example"
        )

        assertThat(dto.toDomain()).isEqualTo(
            User(
                id = 8686808,
                name = "Rep. Kailash Sinha",
                email = "kailash_rep_sinha@gorczany.example"
            )
        )
    }
}
