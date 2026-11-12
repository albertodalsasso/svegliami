package it.svegliami

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class CheckWeatherReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val occ = intent.getLongExtra(Scheduler.EXTRA_OCC, 0)
        val pending = goAsync()
        val app = ctx.applicationContext
        thread {
            try {
                WeatherCheck.runScheduledCheck(app, occ)
            } finally {
                pending.finish()
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Prefs(ctx)
        val kind = intent.getIntExtra(Scheduler.EXTRA_KIND, Scheduler.KIND_NORMAL)
        val occ = intent.getLongExtra(Scheduler.EXTRA_OCC, 0)

        val message = when (kind) {
            Scheduler.KIND_SNOOZE -> "Sveglia posticipata"
            Scheduler.KIND_EARLY, Scheduler.KIND_NORMAL -> {
                if (occ != p.currentOccurrence) return // allarme obsoleto
                p.handledOccurrence = occ
                val msg = if (kind == Scheduler.KIND_EARLY) "☔ Sveglia anticipata per pioggia\n${p.earlyReason}" else "Buongiorno!"
                // Programma la prossima occorrenza; se è suonata quella anticipata, cancella la normale di oggi.
                Scheduler.reschedule(ctx)
                msg
            }
            else -> return
        }
        AlarmService.start(ctx, message)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.reschedule(ctx)
    }
}
