package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.domain.user.User

fun UserDto.toDomain(): User = User(
    id = id,
    name = name,
    email = email
)
