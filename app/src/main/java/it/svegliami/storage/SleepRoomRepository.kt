package it.svegliami.storage

import android.content.Context
import android.util.Log
import it.svegliami.SleepSession
import it.svegliami.SleepStore
import java.util.concurrent.Executors

/** Mirrors compatible legacy session data into Room without changing the JSON source of truth yet. */
object SleepRoomRepository {
    private const val TAG = "SleepRoomRepository"
    private const val MIGRATION_PREFS = "sleep_room_import"
    private const val IMPORTED_KEY = "legacy_v1_imported"
    private val executor = Executors.newSingleThreadExecutor()

    fun importLegacy(context: Context) {
        val app = context.applicationContext
        executor.execute { runCatching { importLegacyNow(app, SleepDatabase.get(app)) }.onFailure { Log.w(TAG, "Legacy session import will retry", it) } }
    }

    fun mirror(context: Context, session: SleepSession) {
        val app = context.applicationContext
        executor.execute {
            runCatching {
                val db = SleepDatabase.get(app)
                importLegacyNow(app, db)
                db.runInTransaction { writeBundle(db, SleepEntityMapper.map(session)) }
                db.sleepDao().trimSessions()
            }.onFailure { Log.w(TAG, "Room mirror failed; the legacy session remains available", it) }
        }
    }

    fun loadJournal(context: Context, sessionId: Long, onResult: (SleepJournalEntryEntity?) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val entry = runCatching {
                val db = SleepDatabase.get(app)
                importLegacyNow(app, db)
                db.sleepDao().journal(sessionId)
            }.getOrNull()
            onResult(entry)
        }
    }

    fun saveJournal(context: Context, entry: SleepJournalEntryEntity, onResult: (Boolean) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val saved = runCatching {
                val db = SleepDatabase.get(app)
                importLegacyNow(app, db)
                if (db.sleepDao().session(entry.sessionId) == null) false
                else {
                    db.sleepDao().putJournal(entry)
                    true
                }
            }.getOrDefault(false)
            onResult(saved)
        }
    }

    private fun importLegacyNow(context: Context, db: SleepDatabase) {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(IMPORTED_KEY, false)) return
        val sessions = SleepStore.load(context)
        db.runInTransaction {
            sessions.forEach { writeBundle(db, SleepEntityMapper.map(it)) }
            db.sleepDao().trimSessions()
        }
        prefs.edit().putBoolean(IMPORTED_KEY, true).commit()
    }

    private fun writeBundle(db: SleepDatabase, bundle: SleepSessionBundle) {
        db.sleepDao().putSession(bundle.session)
        db.sleepDao().putSamples(bundle.samples)
        db.sleepDao().putEvents(bundle.events)
    }
}
