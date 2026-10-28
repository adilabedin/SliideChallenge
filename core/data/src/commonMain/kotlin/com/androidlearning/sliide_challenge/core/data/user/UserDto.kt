package com.androidlearning.sliide_challenge.core.data.user

import kotlinx.serialization.Serializable

/** Wire shape of a GoRest `/public/v2/users` entry. */
@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
    val gender: String,
    val status: String,
)
