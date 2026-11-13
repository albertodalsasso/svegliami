package it.svegliami

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Scheduler {
    const val KIND_NORMAL = 1
    const val KIND_EARLY = 2
    const val KIND_CHECK = 3
    const val KIND_SNOOZE = 4
    private val ALL_KINDS = listOf(KIND_NORMAL, KIND_EARLY, KIND_CHECK, KIND_SNOOZE)

    const val EXTRA_ID = "alarmId"
    const val EXTRA_KIND = "kind"
    const val EXTRA_OCC = "occurrence"

    /** Il controllo meteo avviene questi minuti prima della sveglia anticipata. */
    const val CHECK_LEAD_MIN = 20L
    const val RETRY_MIN = 5L
    const val SNOOZE_MIN = 9L

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM 'alle' HH:mm", Locale.ITALIAN)

    fun fmtTime(ms: Long): String = TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    fun fmtDayTime(ms: Long): String = DAY_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    private fun receiverFor(kind: Int) =
        if (kind == KIND_CHECK) CheckWeatherReceiver::class.java else AlarmReceiver::class.java

    /** Request code univoco per sveglia e tipo (gli id partono da 1, quindi i codici da 11). */
    private fun code(id: Int, kind: Int) = id * 10 + kind

    private fun pi(ctx: Context, id: Int, kind: Int, occ: Long): PendingIntent {
        val i = Intent(ctx, receiverFor(kind))
            .putExtra(EXTRA_ID, id).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_OCC, occ)
        return PendingIntent.getBroadcast(ctx, code(id, kind), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancelCode(ctx: Context, requestCode: Int, kind: Int) {
        PendingIntent.getBroadcast(
            ctx, requestCode, Intent(ctx, receiverFor(kind)),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { am(ctx).cancel(it); it.cancel() }
    }

    private fun cancel(ctx: Context, id: Int, kind: Int) = cancelCode(ctx, code(id, kind), kind)

    private fun showPi(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sveglia vera: setAlarmClock esce dal Doze ed è mostrata nella barra di stato. */
    private fun setClock(ctx: Context, at: Long, op: PendingIntent) {
        if (canExact(ctx)) am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, showPi(ctx)), op)
        else am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    private fun setExact(ctx: Context, at: Long, op: PendingIntent) {
        if (canExact(ctx)) am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        else am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    /** Prossimo orario della sveglia dopo [now] e dopo l'ultima occorrenza già suonata. */
    fun nextOccurrence(a: Alarm, handled: Long, now: Long): Long? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (i in 0..7) {
            val d = today.plusDays(i.toLong())
            if (a.days.isNotEmpty() && d.dayOfWeek.value !in a.days) continue
            val t = d.atTime(a.hour, a.minute).atZone(zone).toInstant().toEpochMilli()
            if (t > now && t > handled) return t
        }
        return null
    }

    /** Ricalcola una sveglia: sveglia normale della prossima occorrenza + controllo meteo. */
    fun reschedule(ctx: Context, id: Int) {
        val p = Prefs(ctx)
        val st = p.state(id)
        cancel(ctx, id, KIND_CHECK)
        cancel(ctx, id, KIND_EARLY)
        cancel(ctx, id, KIND_NORMAL)
        st.earlyAt = 0
        st.currentOccurrence = 0
        val a = p.alarm(id) ?: return
        if (!a.enabled) return

        val now = System.currentTimeMillis()
        val occ = nextOccurrence(a, st.handledOccurrence, now) ?: return
        st.currentOccurrence = occ
        setClock(ctx, occ, pi(ctx, id, KIND_NORMAL, occ))

        if (!a.rainEnabled || (!p.hasLocation && !p.autoLocation)) return
        val early = occ - a.advanceMin * 60_000L
        // Prima anche dell'eventuale finestra intelligente, che si sposta in avanti se piove.
        val check = early - (a.smartWindowMin + CHECK_LEAD_MIN) * 60_000L
        when {
            check > now -> setExact(ctx, check, pi(ctx, id, KIND_CHECK, occ))
            early > now -> setExact(ctx, now + 2_000, pi(ctx, id, KIND_CHECK, occ)) // già nella finestra: controlla subito
        }
    }

    fun rescheduleAll(ctx: Context) {
        // Allarmi della prima versione (una sola sveglia, request code 1–4).
        ALL_KINDS.forEach { cancelCode(ctx, it, it) }
        Prefs(ctx).alarms.forEach { reschedule(ctx, it.id) }
    }

    /** Cancella tutto ciò che riguarda una sveglia (prima di eliminarla). */
    fun cancelAll(ctx: Context, id: Int) = ALL_KINDS.forEach { cancel(ctx, id, it) }

    fun scheduleEarly(ctx: Context, id: Int, occ: Long, early: Long) {
        Prefs(ctx).state(id).earlyAt = early
        setClock(ctx, early, pi(ctx, id, KIND_EARLY, occ))
    }

    fun scheduleCheckRetry(ctx: Context, id: Int, at: Long, occ: Long) = setExact(ctx, at, pi(ctx, id, KIND_CHECK, occ))

    fun scheduleSnooze(ctx: Context, id: Int) =
        setClock(ctx, System.currentTimeMillis() + SNOOZE_MIN * 60_000L, pi(ctx, id, KIND_SNOOZE, 0))
}
