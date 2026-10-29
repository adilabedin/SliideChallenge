package com.androidlearning.sliide_challenge.userfeed.data.di

import com.androidlearning.sliide_challenge.userfeed.data.sync.PendingSyncScheduling
import com.androidlearning.sliide_challenge.userfeed.data.sync.UserSyncWorker
import com.androidlearning.sliide_challenge.userfeed.data.sync.WorkManagerUserSyncScheduler
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Background sync on Android (WorkManager); the app loads this alongside [userFeedDataModule]. */
val userFeedSyncModule: Module = module {
    singleOf(::WorkManagerUserSyncScheduler) bind UserSyncScheduler::class
    singleOf(::PendingSyncScheduling)
    workerOf(::UserSyncWorker)
}
