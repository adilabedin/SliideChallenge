package com.androidlearning.sliide_challenge

import com.androidlearning.sliide_challenge.di.initKoin
import com.androidlearning.sliide_challenge.userfeed.data.di.userFeedSyncModule
import com.androidlearning.sliide_challenge.userfeed.data.sync.IosUserSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.mp.KoinPlatform

/**
 * Called from `iOSApp.init()`, before launch finishes, because iOS requires background task handlers to be
 * registered by then. Starts Koin, so [MainViewController] must not.
 */
fun startIosApp() {
    initKoin {
        modules(userFeedSyncModule())
    }
    KoinPlatform.getKoin().get<IosUserSync>().start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
}
