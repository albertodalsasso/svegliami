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
    private const val JOURNAL_CLEARED_KEY = "journal_cleared"
    private val executor = Executors.newSingleThreadExecutor()

    fun importLegacy(context: Context) {
        val app = context.applicationContext
        executor.execute {
            runCatching { importLegacyNow(app, SleepDatabase.get(app)) }.onFailure { Log.w(TAG, "Legacy session import will retry", it) }
            clearJournalOnce(app)
        }
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

    /** Il diario è stato tolto: le annotazioni salvate dalle versioni precedenti vengono cancellate una volta. */
    private fun clearJournalOnce(context: Context) {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(JOURNAL_CLEARED_KEY, false)) return
        runCatching { SleepDatabase.get(context).sleepDao().clearJournal() }
            .onSuccess { prefs.edit().putBoolean(JOURNAL_CLEARED_KEY, true).apply() }
            .onFailure { Log.w(TAG, "Journal cleanup will retry", it) }
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

    /** Aggiorna la sessione; campioni ed eventi vengono riscritti per intero. */
    private fun writeBundle(db: SleepDatabase, bundle: SleepSessionBundle) {
        val dao = db.sleepDao()
        dao.putSession(bundle.session)
        dao.deleteSamples(bundle.session.id)
        dao.deleteEvents(bundle.session.id)
        dao.putSamples(bundle.samples)
        dao.putEvents(bundle.events)
    }
}
