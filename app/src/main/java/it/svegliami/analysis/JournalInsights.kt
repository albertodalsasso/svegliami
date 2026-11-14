package it.svegliami.analysis

import java.time.Instant
import java.time.ZoneId

/** Una notte registrata con le annotazioni del diario (campi null = non annotati). */
data class JournalNight(
    val start: Long,
    val sleepMinutes: Int,
    val score: Int,
    val caffeine: Boolean?,
    val exercise: Boolean?,
    val stress: Int?,
    val mealTime: String?,
)

/** Confronto fra le notti con un'abitudine e quelle senza. */
data class HabitComparison(
    val habit: String,
    val withLabel: String,
    val withoutLabel: String,
    val withNights: Int,
    val withoutNights: Int,
    val withSleepMinutes: Int,
    val withoutSleepMinutes: Int,
    val withScore: Int,
    val withoutScore: Int,
) {
    val sleepDifferenceMinutes get() = withSleepMinutes - withoutSleepMinutes
}

/**
 * Mette a confronto le notti in base alle abitudini annotate nel diario: caffeina, attività
 * fisica, stress, cena tardi. Un'abitudine compare solo se ci sono almeno [MIN_NIGHTS] notti
 * con e [MIN_NIGHTS] senza: con meno dati il confronto non dice niente. Sono medie, non cause.
 */
object JournalInsights {
    const val MIN_NIGHTS = 2
    /** Cena "tardi" se finita meno di 2 ore prima di andare a letto. */
    const val LATE_MEAL_MINUTES = 120

    fun compare(nights: List<JournalNight>, zoneId: ZoneId = ZoneId.systemDefault()): List<HabitComparison> = listOfNotNull(
        comparison(nights, "Caffeina", "Con caffeina", "Senza caffeina") { it.caffeine },
        comparison(nights, "Attività fisica", "Con attività fisica", "Senza attività fisica") { it.exercise },
        comparison(nights, "Stress", "Stress alto", "Stress normale o basso") { n -> n.stress?.let { it >= 3 } },
        comparison(nights, "Cena", "Cena tardi (meno di 2 h prima)", "Cena prima") { lateMeal(it, zoneId) },
    )

    private fun comparison(
        nights: List<JournalNight>,
        habit: String,
        withLabel: String,
        withoutLabel: String,
        has: (JournalNight) -> Boolean?,
    ): HabitComparison? {
        val with = nights.filter { has(it) == true }
        val without = nights.filter { has(it) == false }
        if (with.size < MIN_NIGHTS || without.size < MIN_NIGHTS) return null
        return HabitComparison(
            habit, withLabel, withoutLabel, with.size, without.size,
            with.map { it.sleepMinutes }.average().toInt(), without.map { it.sleepMinutes }.average().toInt(),
            with.map { it.score }.average().toInt(), without.map { it.score }.average().toInt(),
        )
    }

    /** Null se l'orario del pasto manca o non si capisce (formati accettati: 20:30, 20.30, 20). */
    fun lateMeal(night: JournalNight, zoneId: ZoneId): Boolean? {
        val meal = parseMinutes(night.mealTime ?: return null) ?: return null
        val bedTime = Instant.ofEpochMilli(night.start).atZone(zoneId).toLocalTime()
        // Orari dopo mezzanotte e prima di mezzogiorno contano come "più tardi" della sera.
        fun evening(min: Int) = if (min < 12 * 60) min + 24 * 60 else min
        val bed = evening(bedTime.hour * 60 + bedTime.minute)
        return bed - evening(meal) < LATE_MEAL_MINUTES
    }

    fun parseMinutes(text: String): Int? {
        val parts = text.trim().split(':', '.', ',', 'h', ' ').filter { it.isNotBlank() }
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
