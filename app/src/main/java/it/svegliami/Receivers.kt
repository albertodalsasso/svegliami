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
        AlarmFire.ring(
            ctx, id,
            intent.getIntExtra(Scheduler.EXTRA_KIND, Scheduler.KIND_NORMAL),
            intent.getLongExtra(Scheduler.EXTRA_OCC, 0),
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.rescheduleAll(ctx)
    }
}
