package com.autodeck.companion

import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.TestCarContext
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autodeck.companion.car.NowPlayingCarScreen
import com.autodeck.companion.data.local.AutoDeckDatabase
import com.autodeck.companion.data.local.ParkingSpotEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoDeckInstrumentedTest {

    private lateinit var db: AutoDeckDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutoDeckDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun parkingSpot_insertsAndRetrievesLatest() = runTest {
        db.parkingSpotDao().insert(
            ParkingSpotEntity(
                latitude = 3.0738,
                longitude = 101.5183,
                level = "B2",
                bay = "C14",
                notes = "Near lift lobby",
                savedAtMillis = 1_700_000_000_000L
            )
        )
        val latest = db.parkingSpotDao().getLatest()
        assertEquals("B2", latest?.level)
        assertEquals("C14", latest?.bay)
    }

    @Test
    fun nowPlayingCarScreen_returnsValidPaneTemplate() {
        val testCarContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val screen = NowPlayingCarScreen(testCarContext)
        val template = screen.onGetTemplate()
        assertTrue(template is PaneTemplate)
    }
}
