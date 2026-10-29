package com.androidlearning.sliide_challenge.core.domain.util

import kotlinx.coroutines.CoroutineScope

/**
 * Lives as long as the process, for work that must finish even if the screen or worker that started it goes away
 * (e.g. a save the user confirmed just before closing the screen). Its own type so it can be injected without a
 * qualifier; tests wrap a test scope.
 */
class ApplicationScope(scope: CoroutineScope) : CoroutineScope by scope
