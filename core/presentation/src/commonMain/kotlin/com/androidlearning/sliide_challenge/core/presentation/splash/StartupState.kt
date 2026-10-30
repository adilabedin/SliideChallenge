package com.androidlearning.sliide_challenge.core.presentation.splash

/** How the app's start went. The feed follows it, to shimmer while loading and to show a failed first load. */
enum class StartupState {
    /** The database is opening and, if nothing is cached, the first page is loading. */
    Loading,

    /** Users were cached, or the first page loaded. */
    Loaded,

    /** Nothing was cached and the first page failed to load. */
    Error
}
