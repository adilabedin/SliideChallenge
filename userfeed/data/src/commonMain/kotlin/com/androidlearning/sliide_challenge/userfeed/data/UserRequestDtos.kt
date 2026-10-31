package com.androidlearning.sliide_challenge.userfeed.data

import kotlinx.serialization.Serializable

/**
 * Body of `POST /users`. GoRest rejects a create without `gender` and `status` (422), but the Add User form only asks
 * for name and email, so fixed placeholder values are sent. They are not constructor defaults: the JSON encoder omits
 * properties left at their default, which would drop them from the request.
 */
@Serializable
data class CreateUserRequestDto(val name: String, val email: String, val gender: String, val status: String) {
    companion object {
        const val DEFAULT_GENDER = "female"
        const val DEFAULT_STATUS = "active"
    }
}

/** Body of `PATCH /users/{id}`; only the fields the app edits. */
@Serializable
data class UpdateUserRequestDto(val name: String, val email: String)
