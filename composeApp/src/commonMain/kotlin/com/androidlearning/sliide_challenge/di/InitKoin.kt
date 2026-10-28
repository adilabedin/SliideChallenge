package com.androidlearning.sliide_challenge.di

import com.androidlearning.sliide_challenge.core.data.di.coreDataModule
import com.androidlearning.sliide_challenge.userfeed.presentation.di.userFeedPresentationModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            appModule(),
            coreDataModule,
            userFeedPresentationModule,
        )
    }
}
