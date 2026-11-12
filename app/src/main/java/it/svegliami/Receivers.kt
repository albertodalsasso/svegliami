package it.svegliami

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class CheckWeatherReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        if (id < 0) return
        val occ = intent.getLongExtra(Scheduler.EXTRA_OCC, 0)
        val pending = goAsync()
        val app = ctx.applicationContext
        thread {
            try {
                WeatherCheck.runScheduledCheck(app, id, occ)
            } finally {
                pending.finish()
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        if (id < 0) return // allarme della versione precedente
        val p = Prefs(ctx)
        val a = p.alarm(id) ?: return
        val st = p.state(id)
        val kind = intent.getIntExtra(Scheduler.EXTRA_KIND, Scheduler.KIND_NORMAL)
        val occ = intent.getLongExtra(Scheduler.EXTRA_OCC, 0)

        val message = when (kind) {
            Scheduler.KIND_SNOOZE -> "${a.title}\nPosticipata di ${Scheduler.SNOOZE_MIN} minuti"
            Scheduler.KIND_EARLY, Scheduler.KIND_NORMAL -> {
                if (occ != st.currentOccurrence) return // allarme obsoleto
                st.handledOccurrence = occ
                val msg = if (kind == Scheduler.KIND_EARLY) "${a.title}\n☔ Anticipata per pioggia\n${st.earlyReason}" else a.title
                // Sveglia singola: dopo aver suonato si disattiva.
                if (a.days.isEmpty()) {
                    a.enabled = false
                    p.saveAlarm(a)
                }
                // Programma la prossima occorrenza; se è suonata l'anticipata, cancella la normale di oggi.
                Scheduler.reschedule(ctx, id)
                msg
            }
            else -> return
        }
        AlarmService.start(ctx, id, message)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.rescheduleAll(ctx)
    }
}
