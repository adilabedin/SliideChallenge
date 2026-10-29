package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

sealed interface UserFeedAction {
    /** Edits [user] in [FeedMode.Edit] and ticks or unticks it in [FeedMode.Delete]. */
    data class OnUserClick(val user: FeedUserUi) : UserFeedAction

    /** Asks for confirmation before deleting [user]; only while browsing. */
    data class OnUserLongClick(val user: FeedUserUi) : UserFeedAction

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
     * A delete already confirmed on another screen, e.g. the user's detail screen. It is held back for Undo exactly
     * like one confirmed here, so the feed can offer Undo once the user is back on it.
     */
    data class OnDeleteRequested(val id: FeedUserId) : UserFeedAction
}
