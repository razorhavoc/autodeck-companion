package com.autodeck.companion.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey val widgetKey: String,
    val enabled: Boolean,
    val sortOrder: Int
)

@Entity(tableName = "parking_spots")
data class ParkingSpotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val level: String,
    val bay: String,
    val notes: String,
    val savedAtMillis: Long
)

@Entity(tableName = "cached_prayer_times")
data class CachedPrayerEntity(
    @PrimaryKey val zoneAndDate: String,
    val zone: String,
    val dateLabel: String,
    val fajr: String,
    val syuruk: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val source: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "cached_fuel_prices")
data class CachedFuelEntity(
    @PrimaryKey val id: Int = 1,
    val effectiveDate: String,
    val ron95: Double,
    val ron95Budi: Double?,
    val ron95Skps: Double?,
    val ron97: Double,
    val dieselPeninsular: Double,
    val dieselEastMalaysia: Double?,
    val updatedAtMillis: Long
)

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_configs ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<WidgetConfigEntity>>

    @Query("SELECT * FROM widget_configs ORDER BY sortOrder ASC")
    suspend fun getAll(): List<WidgetConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WidgetConfigEntity>)
}

@Dao
interface ParkingSpotDao {
    @Query("SELECT * FROM parking_spots ORDER BY savedAtMillis DESC LIMIT 1")
    fun observeLatest(): Flow<ParkingSpotEntity?>

    @Query("SELECT * FROM parking_spots ORDER BY savedAtMillis DESC LIMIT 1")
    suspend fun getLatest(): ParkingSpotEntity?

    @Query("SELECT * FROM parking_spots ORDER BY savedAtMillis DESC LIMIT 10")
    fun observeHistory(): Flow<List<ParkingSpotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spot: ParkingSpotEntity): Long

    @Query("DELETE FROM parking_spots WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CachedDataDao {
    @Query("SELECT * FROM cached_prayer_times WHERE zoneAndDate = :key LIMIT 1")
    suspend fun getPrayer(key: String): CachedPrayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrayer(entity: CachedPrayerEntity)

    @Query("SELECT * FROM cached_fuel_prices WHERE id = 1 LIMIT 1")
    suspend fun getFuel(): CachedFuelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFuel(entity: CachedFuelEntity)
}

@Database(
    entities = [
        WidgetConfigEntity::class,
        ParkingSpotEntity::class,
        CachedPrayerEntity::class,
        CachedFuelEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AutoDeckDatabase : RoomDatabase() {
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun parkingSpotDao(): ParkingSpotDao
    abstract fun cachedDataDao(): CachedDataDao

    companion object {
        fun build(context: Context): AutoDeckDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AutoDeckDatabase::class.java,
                "autodeck_companion.db"
            ).build()
    }
}
