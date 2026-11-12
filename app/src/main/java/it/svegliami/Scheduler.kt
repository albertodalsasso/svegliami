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

    private fun pi(ctx: Context, kind: Int, occ: Long): PendingIntent {
        val i = Intent(ctx, receiverFor(kind)).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_OCC, occ)
        return PendingIntent.getBroadcast(ctx, kind, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancel(ctx: Context, kind: Int) {
        PendingIntent.getBroadcast(
            ctx, kind, Intent(ctx, receiverFor(kind)),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { am(ctx).cancel(it); it.cancel() }
    }

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

    fun nextOccurrence(p: Prefs, now: Long): Long? {
        val days = p.days
        if (days.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (i in 0..7) {
            val d = today.plusDays(i.toLong())
            if (d.dayOfWeek.value !in days) continue
            val t = d.atTime(p.hour, p.minute).atZone(zone).toInstant().toEpochMilli()
            if (t > now && t > p.handledOccurrence) return t
        }
        return null
    }

    /** Ricalcola tutto: sveglia normale della prossima occorrenza + controllo meteo. */
    fun reschedule(ctx: Context) {
        val p = Prefs(ctx)
        cancel(ctx, KIND_CHECK)
        cancel(ctx, KIND_EARLY)
        cancel(ctx, KIND_NORMAL)
        p.earlyAt = 0
        p.currentOccurrence = 0
        if (!p.enabled) return

        val now = System.currentTimeMillis()
        val occ = nextOccurrence(p, now) ?: return
        p.currentOccurrence = occ
        setClock(ctx, occ, pi(ctx, KIND_NORMAL, occ))

        if (!p.hasLocation && !p.autoLocation) return
        val early = occ - p.advanceMin * 60_000L
        val check = early - CHECK_LEAD_MIN * 60_000L
        when {
            check > now -> setExact(ctx, check, pi(ctx, KIND_CHECK, occ))
            early > now -> setExact(ctx, now + 2_000, pi(ctx, KIND_CHECK, occ)) // siamo già nella finestra: controlla subito
        }
    }

    fun scheduleEarly(ctx: Context, occ: Long, early: Long) {
        Prefs(ctx).earlyAt = early
        setClock(ctx, early, pi(ctx, KIND_EARLY, occ))
    }

    fun scheduleCheckRetry(ctx: Context, at: Long, occ: Long) = setExact(ctx, at, pi(ctx, KIND_CHECK, occ))

    fun scheduleSnooze(ctx: Context) =
        setClock(ctx, System.currentTimeMillis() + SNOOZE_MIN * 60_000L, pi(ctx, KIND_SNOOZE, 0))
}
