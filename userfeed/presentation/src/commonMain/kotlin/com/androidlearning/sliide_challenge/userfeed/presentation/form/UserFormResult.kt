package com.androidlearning.sliide_challenge.userfeed.presentation.form

import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.form_changes_saved
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.form_user_added
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.form_user_not_found
import org.jetbrains.compose.resources.StringResource

/**
 * Why the form closed itself, handed back to the feed to announce. The form is gone by the time the message shows, so
 * the feed, not the form, shows it.
 */
enum class UserFormResult(val message: StringResource) {
    USER_ADDED(Res.string.form_user_added),
    CHANGES_SAVED(Res.string.form_changes_saved),
    USER_NOT_FOUND(Res.string.form_user_not_found)
}
