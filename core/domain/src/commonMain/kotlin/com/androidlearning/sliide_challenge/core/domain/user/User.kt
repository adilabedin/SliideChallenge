package com.androidlearning.sliide_challenge.core.domain.user

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
)

enum class Gender {
    MALE,
    FEMALE,
    UNKNOWN,
}

enum class UserStatus {
    ACTIVE,
    INACTIVE,
    UNKNOWN,
}
