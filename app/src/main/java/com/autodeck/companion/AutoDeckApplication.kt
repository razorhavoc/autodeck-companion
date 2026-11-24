package com.autodeck.companion

import android.app.Application
import com.autodeck.companion.di.AppContainer
import com.autodeck.companion.service.PrayerReminderWorker

class AutoDeckApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PrayerReminderWorker.schedule(this)
    }
}
