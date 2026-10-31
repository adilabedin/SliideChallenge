package com.androidlearning.sliide_challenge

import com.androidlearning.sliide_challenge.core.domain.util.ApplicationScope
import com.androidlearning.sliide_challenge.di.initKoin
import com.androidlearning.sliide_challenge.userfeed.data.di.userFeedSyncModule
import com.androidlearning.sliide_challenge.userfeed.data.sync.IosUserSync
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.mp.KoinPlatform

/**
 * Called from `iOSApp.init()`, before launch finishes, because iOS requires background task handlers to be
 * registered by then. Starts Koin, so [MainViewController] must not.
 */
@OptIn(ExperimentalNativeApi::class)
fun startIosApp() {
    initKoin(isDebugBuild = Platform.isDebugBinary) {
        modules(userFeedSyncModule)
    }
    val koin = KoinPlatform.getKoin()
    koin.get<IosUserSync>().start(koin.get<ApplicationScope>())
}
