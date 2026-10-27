package com.androidlearning.sliide_challenge

import android.app.Application
import com.androidlearning.sliide_challenge.di.initKoin
import org.koin.android.ext.koin.androidContext

class SliideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@SliideApplication)
        }
    }
}
