package com.androidlearning.sliide_challenge.core.data.user

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.domain.user.Gender
import com.androidlearning.sliide_challenge.core.domain.user.User
import com.androidlearning.sliide_challenge.core.domain.user.UserStatus
import kotlin.test.Test

class UserMapperTest {

    @Test
    fun `toDomain maps all fields`() {
        val dto = UserDto(
            id = 8686808,
            name = "Rep. Kailash Sinha",
            email = "kailash_rep_sinha@gorczany.example",
            gender = "female",
            status = "inactive",
        )

        assertThat(dto.toDomain()).isEqualTo(
            User(
                id = 8686808,
                name = "Rep. Kailash Sinha",
                email = "kailash_rep_sinha@gorczany.example",
                gender = Gender.FEMALE,
                status = UserStatus.INACTIVE,
            ),
        )
    }

    @Test
    fun `toDomain is case insensitive for gender and status`() {
        val user = UserDto(1, "A", "a@b.c", gender = "MALE", status = "Active").toDomain()

        assertThat(user.gender).isEqualTo(Gender.MALE)
        assertThat(user.status).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    fun `toDomain falls back to UNKNOWN for unexpected values`() {
        val user = UserDto(1, "A", "a@b.c", gender = "other", status = "pending").toDomain()

        assertThat(user.gender).isEqualTo(Gender.UNKNOWN)
        assertThat(user.status).isEqualTo(UserStatus.UNKNOWN)
    }
}
