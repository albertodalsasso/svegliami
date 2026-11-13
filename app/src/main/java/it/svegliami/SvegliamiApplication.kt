package it.svegliami

import android.app.Application
import it.svegliami.storage.SleepRoomRepository

class SvegliamiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SleepRoomRepository.importLegacy(this)
    }
}
