package com.autodeck.companion.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.autodeck.companion.BuildConfig

class AutoDeckCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = AutoDeckCarSession()
}

class AutoDeckCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return DashboardTabScreen(carContext)
    }
}
