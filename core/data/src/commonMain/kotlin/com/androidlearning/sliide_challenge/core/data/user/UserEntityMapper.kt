package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.core.domain.user.User

/** [syncedAt] is epoch milliseconds of the fetch that returned this user. */
fun User.toEntity(syncedAt: Long): UserEntity = UserEntity(
    id = id,
    name = name,
    email = email,
    lastSyncedAt = syncedAt
)

fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    email = email
)
