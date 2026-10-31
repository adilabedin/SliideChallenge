package com.androidlearning.sliide_challenge.di

import com.androidlearning.sliide_challenge.core.data.di.coreDataModule
import com.androidlearning.sliide_challenge.core.database.di.coreDatabaseModule
import com.androidlearning.sliide_challenge.core.presentation.di.corePresentationModule
import com.androidlearning.sliide_challenge.userfeed.data.di.userFeedDataModule
import com.androidlearning.sliide_challenge.userfeed.presentation.di.userFeedPresentationModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/** [isDebugBuild] turns on debug-only behaviour such as HTTP logging. */
fun initKoin(isDebugBuild: Boolean, config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            // core
            coreDatabaseModule,
            coreDataModule(enableHttpLogging = isDebugBuild),
            corePresentationModule,
            // features
            userFeedDataModule,
            userFeedPresentationModule
        )
    }
}
