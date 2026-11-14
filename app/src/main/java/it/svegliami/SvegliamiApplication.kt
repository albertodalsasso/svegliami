package it.svegliami

import android.app.Application
import it.svegliami.storage.SleepRoomRepository

class SvegliamiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Un arresto anomalo dell'app finisce nel registro diagnostico prima che il processo si chiuda.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            SleepLog.log(this, "ERRORE (${thread.name}): ${error.stackTraceToString().lines().take(8).joinToString(" | ")}")
            previous?.uncaughtException(thread, error)
        }
        SleepRoomRepository.importLegacy(this)
    }
}
