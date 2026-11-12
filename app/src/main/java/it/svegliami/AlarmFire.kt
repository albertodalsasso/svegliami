package it.svegliami

import android.content.Context

/** Fa suonare una sveglia: usato dagli allarmi programmati e dalla sveglia intelligente. */
object AlarmFire {
    /** Sveglia intelligente (sonno leggero rilevato): non è un allarme programmato. */
    const val KIND_SMART = 5

    fun ring(ctx: Context, id: Int, kind: Int, occ: Long) {
        val p = Prefs(ctx)
        val a = p.alarm(id) ?: return
        val st = p.state(id)

        val message = when (kind) {
            Scheduler.KIND_SNOOZE -> "${a.title}\nPosticipata di ${Scheduler.SNOOZE_MIN} minuti"
            Scheduler.KIND_EARLY, Scheduler.KIND_NORMAL, KIND_SMART -> {
                if (occ != st.currentOccurrence) return // allarme obsoleto o già suonato
                st.handledOccurrence = occ
                val rain = if (st.earlyAt > 0) "\n☔ Anticipata per pioggia\n${st.earlyReason}" else ""
                val msg = when (kind) {
                    KIND_SMART -> "${a.title}\n🌙 Sveglia intelligente: sonno leggero alle ${Scheduler.fmtTime(System.currentTimeMillis())}$rain"
                    Scheduler.KIND_EARLY -> "${a.title}$rain"
                    else -> a.title
                }
                // Sveglia singola: dopo aver suonato si disattiva.
                if (a.days.isEmpty()) {
                    a.enabled = false
                    p.saveAlarm(a)
                }
                // Programma la prossima occorrenza e cancella gli allarmi rimasti di questa.
                Scheduler.reschedule(ctx, id)
                msg
            }
            else -> return
        }
        AlarmService.start(ctx, id, message)
    }
}
