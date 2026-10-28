package com.androidlearning.sliide_challenge.userfeed.data.di

import com.androidlearning.sliide_challenge.userfeed.data.sync.BackgroundTaskUserSyncScheduler
import com.androidlearning.sliide_challenge.userfeed.data.sync.IosUserSync
import com.androidlearning.sliide_challenge.userfeed.data.sync.PendingSyncScheduling
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Background sync on iOS (BGTaskScheduler); the app loads this alongside [userFeedDataModule]. */
fun userFeedSyncModule(): Module =
    module {
        singleOf(::BackgroundTaskUserSyncScheduler) bind UserSyncScheduler::class
        singleOf(::PendingSyncScheduling)
        singleOf(::IosUserSync)
    }
