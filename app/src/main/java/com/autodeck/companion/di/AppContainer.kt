package com.autodeck.companion.di

import android.content.Context
import com.autodeck.companion.data.local.AutoDeckDatabase
import com.autodeck.companion.data.local.UserPreferencesStore
import com.autodeck.companion.data.repository.AutoDeckRepository
import com.autodeck.companion.service.LocationProvider

class AppContainer(context: Context) {
    val database: AutoDeckDatabase = AutoDeckDatabase.build(context)
    val preferencesStore: UserPreferencesStore = UserPreferencesStore(context)
    val locationProvider: LocationProvider = LocationProvider(context)
    val repository: AutoDeckRepository = AutoDeckRepository(
        context = context.applicationContext,
        database = database,
        preferencesStore = preferencesStore,
        locationProvider = locationProvider
    )
}
