package it.svegliami.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Upsert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "sleep_sessions", indices = [Index(value = ["startTime", "endTime"])])
data class SleepSessionEntity(
    @PrimaryKey val id: Long,
    val startTime: Long,
    val endTime: Long,
    val estimatedSleepStart: Long?,
    val estimatedSleepEnd: Long?,
    val trackingMode: String,
    val recordingMode: String,
    val totalSleepMinutes: Int,
    val awakeMinutes: Int,
    val score: Int?,
)

@Entity(
    tableName = "sleep_samples",
    foreignKeys = [ForeignKey(entity = SleepSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index(value = ["sessionId", "timestamp"])],
)
data class SleepSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val movementScore: Float?,
    val soundScore: Float?,
    val estimatedStage: String?,
)

@Entity(
    tableName = "sleep_events",
    foreignKeys = [ForeignKey(entity = SleepSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index(value = ["sessionId", "timestamp"])],
)
data class SleepEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val eventType: String,
    val durationMillis: Long,
    val confidence: Float?,
    val audioFileId: Long?,
)

@Entity(
    tableName = "audio_recordings",
    foreignKeys = [ForeignKey(entity = SleepSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index(value = ["sessionId", "startTime"])],
)
data class AudioRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val filePath: String,
    val startTime: Long,
    val endTime: Long,
    val recordingType: String,
    val format: String,
    val fileSize: Long,
)

@Entity(
    tableName = "sleep_journal_entries",
    foreignKeys = [ForeignKey(entity = SleepSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId"], unique = true)],
)
data class SleepJournalEntryEntity(
    @PrimaryKey val sessionId: Long,
    val caffeine: Boolean?,
    val exercise: Boolean?,
    val stress: Int?,
    val mealTime: String?,
    val mood: String?,
    val notes: String?,
)

@Entity(tableName = "transport_profiles", indices = [Index(value = ["name"], unique = true)])
data class TransportProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val preparationMinutes: Int,
    val travelMinutes: Int,
    val departureTime: String?,
    val weatherConditionsJson: String,
)

@Entity(
    tableName = "alarms",
    foreignKeys = [ForeignKey(entity = TransportProfileEntity::class, parentColumns = ["id"], childColumns = ["transportProfileId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("transportProfileId")],
)
data class AlarmEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val time: String,
    val daysJson: String,
    val enabled: Boolean,
    val transportProfileId: Long?,
    val wakeupWindowMinutes: Int,
    val weatherSettingsJson: String,
)

@Dao
interface SleepDao {
    // Upsert e non REPLACE: in SQLite REPLACE cancella la riga e la reinserisce, e la cancellazione
    // eliminerebbe a cascata diario, campioni ed eventi della sessione.
    @Upsert fun putSession(session: SleepSessionEntity)
    @Insert fun putSamples(samples: List<SleepSampleEntity>)
    @Insert fun putEvents(events: List<SleepEventEntity>)
    @Query("DELETE FROM sleep_samples WHERE sessionId = :sessionId") fun deleteSamples(sessionId: Long)
    @Query("DELETE FROM sleep_events WHERE sessionId = :sessionId") fun deleteEvents(sessionId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putJournal(entry: SleepJournalEntryEntity)
    @Query("SELECT COUNT(*) FROM sleep_sessions") fun sessionCount(): Int
    @Query("SELECT * FROM sleep_sessions WHERE id = :sessionId LIMIT 1") fun session(sessionId: Long): SleepSessionEntity?
    @Query("SELECT * FROM sleep_journal_entries WHERE sessionId = :sessionId") fun journal(sessionId: Long): SleepJournalEntryEntity?
    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC") fun sessions(): List<SleepSessionEntity>
    @Query("DELETE FROM sleep_sessions WHERE id NOT IN (SELECT id FROM sleep_sessions ORDER BY startTime DESC LIMIT 30)") fun trimSessions()
}

@Database(
    entities = [
        SleepSessionEntity::class,
        SleepSampleEntity::class,
        SleepEventEntity::class,
        AudioRecordingEntity::class,
        SleepJournalEntryEntity::class,
        AlarmEntity::class,
        TransportProfileEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SleepDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao

    companion object {
        @Volatile private var instance: SleepDatabase? = null

        fun get(context: Context): SleepDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SleepDatabase::class.java, "svegliami_sleep.db")
                .build().also { instance = it }
        }
    }
}
