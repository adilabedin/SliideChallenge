package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.core.domain.user.User

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    name = name,
    email = email,
)

fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
)
