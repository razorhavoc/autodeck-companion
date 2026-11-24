package com.autodeck.companion.util

import com.autodeck.companion.model.LyricLine
import com.autodeck.companion.model.NextPrayerInfo
import com.autodeck.companion.model.PrayerTimesDay
import java.time.Duration
import java.time.LocalTime
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object LrcParser {
    private val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

    fun parse(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        lrcContent.lineSequence().forEach { rawLine ->
            val match = lineRegex.find(rawLine.trim()) ?: return@forEach
            val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
            val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
            val fractionStr = match.groupValues[3]
            val millis = when (fractionStr.length) {
                0 -> 0L
                1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                else -> fractionStr.take(3).toLongOrNull() ?: 0L
            }
            val lyricText = match.groupValues[4].trim()
            if (lyricText.isNotEmpty()) {
                result.add(
                    LyricLine(
                        timestampMs = minutes * 60_000L + seconds * 1_000L + millis,
                        text = lyricText
                    )
                )
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var active = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= positionMs) active = i else break
        }
        return active
    }
}

object PrayerCalculatorUtil {
    fun parseTime(raw: String): LocalTime? {
        val clean = raw.trim().substringBefore(" ").take(8)
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
    }

    fun findNextPrayer(day: PrayerTimesDay, now: LocalTime = LocalTime.now()): NextPrayerInfo {
        val schedule = listOf(
            "Subuh" to day.fajr,
            "Syuruk" to day.syuruk,
            "Zohor" to day.dhuhr,
            "Asar" to day.asr,
            "Maghrib" to day.maghrib,
            "Isyak" to day.isha
        )
        for ((name, rawTime) in schedule) {
            val prayerTime = parseTime(rawTime) ?: continue
            if (prayerTime.isAfter(now)) {
                val diff = Duration.between(now, prayerTime)
                return NextPrayerInfo(
                    name = name,
                    timeText = formatShort(prayerTime),
                    remainingText = formatDuration(diff)
                )
            }
        }
        val tomorrowSubuh = parseTime(day.fajr) ?: LocalTime.of(5, 45)
        val untilMidnight = Duration.between(now, LocalTime.MAX).plusSeconds(1)
        val total = untilMidnight.plus(Duration.between(LocalTime.MIN, tomorrowSubuh))
        return NextPrayerInfo(
            name = "Subuh (Tomorrow)",
            timeText = formatShort(tomorrowSubuh),
            remainingText = formatDuration(total)
        )
    }

    private fun formatShort(time: LocalTime): String =
        String.format(Locale.US, "%02d:%02d", time.hour, time.minute)

    private fun formatDuration(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

object QiblaCalculator {
    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262

    fun bearingDegrees(latitude: Double, longitude: Double): Double {
        val latRad = Math.toRadians(latitude)
        val kaabaLatRad = Math.toRadians(KAABA_LAT)
        val deltaLon = Math.toRadians(KAABA_LON - longitude)
        val y = sin(deltaLon) * cos(kaabaLatRad)
        val x = cos(latRad) * sin(kaabaLatRad) - sin(latRad) * cos(kaabaLatRad) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}

object DistanceCalculator {
    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}

object WeatherCodeMapper {
    fun describe(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}

data class JakimZoneOption(val code: String, val label: String)

object JakimZones {
    val popular: List<JakimZoneOption> = listOf(
        JakimZoneOption("SGR01", "Selangor - Shah Alam, Petaling, Gombak, Sepang"),
        JakimZoneOption("SGR02", "Selangor - Kuala Selangor, Sabak Bernam"),
        JakimZoneOption("SGR03", "Selangor - Klang, Kuala Langat"),
        JakimZoneOption("WLY01", "KL & Putrajaya"),
        JakimZoneOption("WLY02", "Labuan"),
        JakimZoneOption("JHR02", "Johor - Johor Bahru, Kota Tinggi, Mersing"),
        JakimZoneOption("PNG01", "Pulau Pinang (All Areas)"),
        JakimZoneOption("PRK02", "Perak - Ipoh, Kuala Kangsar, Kampar"),
        JakimZoneOption("MLK01", "Melaka (All Areas)"),
        JakimZoneOption("NGS02", "Negeri Sembilan - Seremban, Port Dickson"),
        JakimZoneOption("PHG02", "Pahang - Kuantan, Pekan, Muadzam Shah"),
        JakimZoneOption("KDH01", "Kedah - Kota Setar, Kubang Pasu"),
        JakimZoneOption("KTN01", "Kelantan - Kota Bharu, Bachok, Pasir Mas"),
        JakimZoneOption("TRG01", "Terengganu - Kuala Terengganu, Marang"),
        JakimZoneOption("SBH07", "Sabah - Kota Kinabalu, Ranau, Tuaran"),
        JakimZoneOption("SWK08", "Sarawak - Kuching, Bau, Lundu, Samarahan")
    )
}
