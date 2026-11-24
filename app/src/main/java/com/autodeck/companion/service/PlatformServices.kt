package com.autodeck.companion.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.autodeck.companion.AutoDeckApplication
import com.autodeck.companion.R
import com.autodeck.companion.model.NowPlayingTrack
import com.autodeck.companion.util.PrayerCalculatorUtil
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object MediaPlaybackTracker {
    private val _nowPlaying = MutableStateFlow(NowPlayingTrack())
    val nowPlaying: StateFlow<NowPlayingTrack> = _nowPlaying.asStateFlow()

    fun updateFromController(controller: MediaController?) {
        if (controller == null) return
        val metadata = controller.metadata
        val state = controller.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        if (title.isNotBlank()) {
            _nowPlaying.value = NowPlayingTrack(
                title = title,
                artist = artist,
                album = album,
                packageName = controller.packageName.orEmpty(),
                isPlaying = isPlaying,
                positionMs = position.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(0L),
                lastUpdatedElapsedMs = SystemClock.elapsedRealtime()
            )
        }
    }

    fun currentEstimatedPositionMs(): Long {
        val current = _nowPlaying.value
        if (!current.isPlaying || current.lastUpdatedElapsedMs == 0L) return current.positionMs
        val delta = SystemClock.elapsedRealtime() - current.lastUpdatedElapsedMs
        return (current.positionMs + delta).coerceAtLeast(0L)
    }

    fun setManualPreviewTrack(title: String, artist: String) {
        _nowPlaying.value = NowPlayingTrack(
            title = title,
            artist = artist,
            isPlaying = false,
            positionMs = 0L,
            lastUpdatedElapsedMs = SystemClock.elapsedRealtime()
        )
    }
}

class MediaMonitorNotificationService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            MediaPlaybackTracker.updateFromController(activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaPlaybackTracker.updateFromController(activeController)
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachPrimaryController(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        mediaSessionManager = manager
        val component = ComponentName(this, MediaMonitorNotificationService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            attachPrimaryController(manager.getActiveSessions(component))
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        detachController()
        runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
    }

    private fun attachPrimaryController(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        if (chosen?.sessionToken == activeController?.sessionToken) {
            MediaPlaybackTracker.updateFromController(chosen)
            return
        }
        detachController()
        activeController = chosen
        activeController?.registerCallback(controllerCallback)
        MediaPlaybackTracker.updateFromController(activeController)
    }

    private fun detachController() {
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
    }
}

data class Coordinates(val latitude: Double, val longitude: Double, val isDefaultFallback: Boolean = false)

class LocationProvider(private val context: Context) {
    // Default fallback: Shah Alam, Selangor, Malaysia
    private val defaultCoords = Coordinates(3.0738, 101.5183, isDefaultFallback = true)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(): Coordinates {
        if (!hasLocationPermission()) return defaultCoords
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val location: Location? = suspendCancellableCoroutine { cont ->
            fused.lastLocation
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
        return if (location != null) {
            Coordinates(location.latitude, location.longitude, isDefaultFallback = false)
        } else {
            defaultCoords
        }
    }
}

class PrayerReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AutoDeckApplication ?: return Result.success()
        val prefs = app.container.repository.getPreferencesSnapshot()
        if (!prefs.azanReminderEnabled) return Result.success()

        val prayerDay = app.container.repository.fetchPrayerTimes(prefs.jakimZone) ?: return Result.success()
        val next = PrayerCalculatorUtil.findNextPrayer(prayerDay)
        if (next.remainingText.endsWith("m") && !next.remainingText.contains("h")) {
            val mins = next.remainingText.removeSuffix("m").toIntOrNull() ?: 99
            if (mins in 0..15) {
                showNotification("Upcoming Prayer: ${next.name}", "${next.name} at ${next.timeText} (in ${next.remainingText})")
            }
        }
        return Result.success()
    }

    private fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "prayer_reminders"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Prayer Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_car_prayer)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PrayerReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "autodeck_prayer_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
