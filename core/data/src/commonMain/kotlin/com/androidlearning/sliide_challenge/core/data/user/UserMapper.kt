package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.domain.user.Gender
import com.androidlearning.sliide_challenge.core.domain.user.User
import com.androidlearning.sliide_challenge.core.domain.user.UserStatus

fun UserDto.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    gender = when (gender.lowercase()) {
        "male" -> Gender.MALE
        "female" -> Gender.FEMALE
        else -> Gender.UNKNOWN
    },
    status = when (status.lowercase()) {
        "active" -> UserStatus.ACTIVE
        "inactive" -> UserStatus.INACTIVE
        else -> UserStatus.UNKNOWN
    },
)
