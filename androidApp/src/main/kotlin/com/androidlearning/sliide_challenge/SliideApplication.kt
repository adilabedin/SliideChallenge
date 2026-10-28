package com.androidlearning.sliide_challenge

import android.app.Application
import com.androidlearning.sliide_challenge.di.initKoin
import com.androidlearning.sliide_challenge.userfeed.data.di.userFeedSyncModule
import com.androidlearning.sliide_challenge.userfeed.data.sync.PendingSyncScheduling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory

class SliideApplication : Application() {
    /** Lives as long as the process, which is how long pending syncs need watching. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@SliideApplication)
            // Initializes WorkManager with Koin's factory; the default initializer is removed in the manifest.
            workManagerFactory()
            modules(userFeedSyncModule())
        }
        get<PendingSyncScheduling>().start(applicationScope)
    }
}
