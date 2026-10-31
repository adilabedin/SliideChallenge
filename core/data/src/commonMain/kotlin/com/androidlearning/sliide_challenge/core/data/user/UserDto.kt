package com.androidlearning.sliide_challenge.core.data.user

import kotlinx.serialization.Serializable

/** Wire shape of a GoRest `/public/v2/users` entry; unused fields (gender, status) are ignored. */
@Serializable
data class UserDto(val id: Long, val name: String, val email: String)
