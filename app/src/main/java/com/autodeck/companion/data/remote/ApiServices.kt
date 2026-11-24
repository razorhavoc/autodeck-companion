package com.autodeck.companion.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- 1. Waktu Solat Malaysia (JAKIM) ---
data class WaktuSolatResponse(
    val zone: String?,
    val prayerTime: List<WaktuSolatDayDto>?
)

data class WaktuSolatDayDto(
    val date: String?,
    val fajr: String?,
    val syuruk: String?,
    val dhuhr: String?,
    val asr: String?,
    val maghrib: String?,
    val isha: String?
)

interface WaktuSolatApi {
    @GET("api/solat/{zone}")
    suspend fun getMonthlyPrayerTimes(
        @Path("zone") zone: String,
        @Query("month") month: Int,
        @Query("year") year: Int
    ): WaktuSolatResponse
}

// --- 2. AlAdhan Global Fallback (Method 17 = JAKIM) ---
data class AlAdhanResponse(val code: Int?, val data: AlAdhanData?)
data class AlAdhanData(val timings: AlAdhanTimings?)
data class AlAdhanTimings(
    @SerializedName("Fajr") val fajr: String?,
    @SerializedName("Sunrise") val sunrise: String?,
    @SerializedName("Dhuhr") val dhuhr: String?,
    @SerializedName("Asr") val asr: String?,
    @SerializedName("Maghrib") val maghrib: String?,
    @SerializedName("Isha") val isha: String?
)

interface AlAdhanApi {
    @GET("v1/timings/{date}")
    suspend fun getTimings(
        @Path("date") ddMmYyyy: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int = 17
    ): AlAdhanResponse
}

// --- 3. Open-Meteo Weather (Free, No Key) ---
data class OpenMeteoResponse(
    val current: OpenMeteoCurrent?,
    val daily: OpenMeteoDaily?
)

data class OpenMeteoCurrent(
    @SerializedName("temperature_2m") val temperature2m: Double?,
    @SerializedName("apparent_temperature") val apparentTemperature: Double?,
    @SerializedName("relative_humidity_2m") val relativeHumidity2m: Int?,
    @SerializedName("weather_code") val weatherCode: Int?,
    @SerializedName("wind_speed_10m") val windSpeed10m: Double?
)

data class OpenMeteoDaily(
    val time: List<String>?,
    @SerializedName("weather_code") val weatherCode: List<Int>?,
    @SerializedName("temperature_2m_max") val tempMax: List<Double>?,
    @SerializedName("temperature_2m_min") val tempMin: List<Double>?,
    @SerializedName("precipitation_probability_max") val precipProbMax: List<Int>?,
    @SerializedName("uv_index_max") val uvIndexMax: List<Double>?
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 4
    ): OpenMeteoResponse
}

// --- 4. Malaysia Official Fuel Price Open API (data.gov.my) ---
data class MalaysiaFuelDto(
    val date: String?,
    @SerializedName("series_type") val seriesType: String?,
    val ron95: Double?,
    val ron97: Double?,
    val diesel: Double?,
    @SerializedName("diesel_eastmsia") val dieselEastMsia: Double?,
    @SerializedName("ron95_budi95") val ron95Budi95: Double?,
    @SerializedName("ron95_skps") val ron95Skps: Double?
)

interface MalaysiaFuelApi {
    @GET("data-catalogue")
    suspend fun getFuelPrices(
        @Query("id") id: String = "fuelprice",
        @Query("limit") limit: Int = 10
    ): List<MalaysiaFuelDto>
}

// --- 5. Open Charge Map EV Chargers (API Key Required) ---
data class OcmPoiDto(
    @SerializedName("ID") val id: Long?,
    @SerializedName("OperatorInfo") val operatorInfo: OcmOperatorDto?,
    @SerializedName("AddressInfo") val addressInfo: OcmAddressDto?,
    @SerializedName("Connections") val connections: List<OcmConnectionDto>?
)

data class OcmOperatorDto(@SerializedName("Title") val title: String?)
data class OcmAddressDto(
    @SerializedName("Title") val title: String?,
    @SerializedName("AddressLine1") val addressLine1: String?,
    @SerializedName("Town") val town: String?,
    @SerializedName("Latitude") val latitude: Double?,
    @SerializedName("Longitude") val longitude: Double?,
    @SerializedName("Distance") val distance: Double?
)
data class OcmConnectionDto(
    @SerializedName("ConnectionType") val connectionType: OcmConnectionTypeDto?,
    @SerializedName("PowerKW") val powerKw: Double?
)
data class OcmConnectionTypeDto(@SerializedName("Title") val title: String?)

interface OpenChargeMapApi {
    @GET("v3/poi/")
    suspend fun getNearbyChargers(
        @Query("output") output: String = "json",
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("distance") distanceKm: Int = 25,
        @Query("distanceunit") distanceUnit: String = "KM",
        @Query("maxresults") maxResults: Int = 10,
        @Query("key") apiKey: String
    ): List<OcmPoiDto>
}

// --- 6. Sports Scores (ESPN Public + Football-Data.org Optional) ---
data class EspnScoreboardResponse(val events: List<EspnEventDto>?)
data class EspnEventDto(
    val id: String?,
    val status: EspnStatusDto?,
    val competitions: List<EspnCompetitionDto>?
)
data class EspnStatusDto(val type: EspnStatusTypeDto?)
data class EspnStatusTypeDto(val shortDetail: String?)
data class EspnCompetitionDto(val competitors: List<EspnCompetitorDto>?)
data class EspnCompetitorDto(
    val homeAway: String?,
    val score: String?,
    val team: EspnTeamDto?
)
data class EspnTeamDto(val shortDisplayName: String?, val displayName: String?)

interface EspnSportsApi {
    @GET("apis/site/v2/sports/soccer/eng.1/scoreboard")
    suspend fun getEplScoreboard(): EspnScoreboardResponse
}

data class FootballDataResponse(val matches: List<FootballDataMatchDto>?)
data class FootballDataMatchDto(
    val id: Long?,
    val status: String?,
    val competition: FootballDataCompetitionDto?,
    val homeTeam: FootballDataTeamDto?,
    val awayTeam: FootballDataTeamDto?,
    val score: FootballDataScoreDto?
)
data class FootballDataCompetitionDto(val name: String?)
data class FootballDataTeamDto(val shortName: String?, val name: String?)
data class FootballDataScoreDto(val fullTime: FootballDataFullTimeDto?)
data class FootballDataFullTimeDto(val home: Int?, val away: Int?)

interface FootballDataApi {
    @GET("v4/matches")
    suspend fun getTodayMatches(
        @Header("X-Auth-Token") apiKey: String
    ): FootballDataResponse
}

// --- 7. LRCLIB Synchronized Lyrics (Free, No Key) ---
data class LrcLibDto(
    val trackName: String?,
    val artistName: String?,
    val syncedLyrics: String?,
    val plainLyrics: String?
)

interface LrcLibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String
    ): LrcLibDto

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcLibDto>
}

object NetworkModule {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "AutoDeckCompanion/1.0 (Android Auto)")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private fun <T> create(baseUrl: String, serviceClass: Class<T>): T =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(serviceClass)

    val waktuSolatApi: WaktuSolatApi by lazy { create("https://api.waktusolat.online/", WaktuSolatApi::class.java) }
    val alAdhanApi: AlAdhanApi by lazy { create("https://api.aladhan.com/", AlAdhanApi::class.java) }
    val openMeteoApi: OpenMeteoApi by lazy { create("https://api.open-meteo.com/", OpenMeteoApi::class.java) }
    val malaysiaFuelApi: MalaysiaFuelApi by lazy { create("https://api.data.gov.my/", MalaysiaFuelApi::class.java) }
    val openChargeMapApi: OpenChargeMapApi by lazy { create("https://api.openchargemap.io/", OpenChargeMapApi::class.java) }
    val espnSportsApi: EspnSportsApi by lazy { create("https://site.api.espn.com/", EspnSportsApi::class.java) }
    val footballDataApi: FootballDataApi by lazy { create("https://api.football-data.org/", FootballDataApi::class.java) }
    val lrcLibApi: LrcLibApi by lazy { create("https://lrclib.net/", LrcLibApi::class.java) }
}
