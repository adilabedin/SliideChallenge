package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.core.presentation.splash.StartupState
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

sealed interface UserFeedAction {
    /** Pulled the list down: sync queued changes and fetch the last GoRest page. */
    data object OnRefresh : UserFeedAction

    /** Opens [user]'s details while browsing, edits them in [FeedMode.Edit] and ticks them in [FeedMode.Delete]. */
    data class OnUserClick(val user: FeedUserUi) : UserFeedAction

    /** Asks for confirmation before deleting [user]; only while browsing. */
    data class OnUserLongClick(val user: FeedUserUi) : UserFeedAction

    /** Closes the opened user's details, e.g. their back arrow, or a user who no longer exists. */
    data object OnCloseUserClick : UserFeedAction

    /** The window changed between showing the details over the list and beside it. */
    data class OnLayoutChange(val showsDetailsBesideList: Boolean) : UserFeedAction

    data object OnEditModeClick : UserFeedAction

    data object OnDeleteModeClick : UserFeedAction

    /** Leaves Edit or Delete mode, dropping any selection. */
    data object OnExitModeClick : UserFeedAction

    /** Asks for confirmation before deleting the users ticked in [FeedMode.Delete]. */
    data object OnDeleteSelectedClick : UserFeedAction

    data object OnDeleteConfirm : UserFeedAction

    data object OnDeleteDismiss : UserFeedAction

    data object OnUndoDeleteClick : UserFeedAction

    /**
     * A delete already confirmed elsewhere, e.g. the user's details. It is held back for Undo exactly
     * like one confirmed here, so the feed can offer Undo once the user is back on it.
     */
    data class OnDeleteRequested(val id: FeedUserId) : UserFeedAction

    /** How the app's start is going; the feed shimmers while it loads and reports a failed first load once. */
    data class OnStartupStateChange(val startupState: StartupState) : UserFeedAction
}
