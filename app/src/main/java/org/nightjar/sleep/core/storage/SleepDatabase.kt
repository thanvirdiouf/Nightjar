// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.core.storage

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SleepSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val mode: String = "ACCELEROMETER",
    val score: Int? = null,
    val latencyMs: Long? = null,
    val sleepMs: Long? = null,
    val awakeMs: Long? = null,
    val deepPercent: Float? = null,
    val awakenings: Int? = null,
    val note: String = "",
    val status: String = "RUNNING",
    val lowThreshold: Float = 0.08f,
    val highThreshold: Float = 0.35f
)

@Entity(tableName = "epochs",
    foreignKeys = [ForeignKey(entity = SleepSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index(value = ["sessionId", "startTime"], unique = true)])
data class Epoch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startTime: Long,
    val durationMs: Long,
    val intensity: Float,
    val activityScore: Float,
    val phase: String,
    val sampleCount: Int
)

@Entity(tableName = "noise_events",
    foreignKeys = [ForeignKey(entity = SleepSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")])
data class NoiseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val durationMs: Long,
    val type: String,
    val clipPath: String? = null
)

@Entity(tableName = "daily_tags",
    primaryKeys = ["sessionId", "tag"],
    foreignKeys = [ForeignKey(entity = SleepSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class DailyTag(val sessionId: Long, val tag: String)

@Dao
interface SleepDao {
    @Query("SELECT * FROM sessions ORDER BY startTime DESC") fun observeSessions(): Flow<List<SleepSession>>
    @Query("SELECT * FROM sessions WHERE id = :id") fun observeSession(id: Long): Flow<SleepSession?>
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: Long): SleepSession?
    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1") suspend fun activeSession(): SleepSession?
    @Query("SELECT * FROM sessions ORDER BY startTime") suspend fun allSessions(): List<SleepSession>
    @Insert suspend fun insertSession(session: SleepSession): Long
    @Update suspend fun updateSession(session: SleepSession)
    @Delete suspend fun deleteSession(session: SleepSession)
    @Query("SELECT * FROM epochs WHERE sessionId = :id ORDER BY startTime") fun observeEpochs(id: Long): Flow<List<Epoch>>
    @Query("SELECT * FROM epochs WHERE sessionId = :id ORDER BY startTime") suspend fun epochs(id: Long): List<Epoch>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEpoch(epoch: Epoch)
    @Update suspend fun updateEpochs(epochs: List<Epoch>)
    @Query("SELECT * FROM noise_events ORDER BY timestamp") suspend fun allNoise(): List<NoiseEvent>
    @Query("SELECT * FROM noise_events WHERE sessionId = :id ORDER BY timestamp") fun observeNoise(id: Long): Flow<List<NoiseEvent>>
    @Query("SELECT * FROM noise_events WHERE sessionId = :id ORDER BY timestamp") suspend fun noise(id: Long): List<NoiseEvent>
    @Query("SELECT * FROM noise_events WHERE clipPath IS NOT NULL AND timestamp < :cutoff") suspend fun expiredClips(cutoff: Long): List<NoiseEvent>
    @Insert suspend fun insertNoise(event: NoiseEvent): Long
    @Update suspend fun updateNoise(event: NoiseEvent)
    @Query("SELECT * FROM daily_tags WHERE sessionId = :id ORDER BY tag") fun observeTags(id: Long): Flow<List<DailyTag>>
    @Query("SELECT * FROM daily_tags ORDER BY sessionId, tag") suspend fun allTags(): List<DailyTag>
    @Query("DELETE FROM daily_tags WHERE sessionId = :id") suspend fun clearTags(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTags(tags: List<DailyTag>)
}

@Database(entities = [SleepSession::class, Epoch::class, NoiseEvent::class, DailyTag::class], version = 1, exportSchema = true)
abstract class SleepDatabase : RoomDatabase() {
    abstract fun dao(): SleepDao
    companion object {
        fun create(context: Context): SleepDatabase = Room.databaseBuilder(context, SleepDatabase::class.java, "nightjar.db").build()
    }
}
