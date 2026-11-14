package it.svegliami

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro diagnostico del monitoraggio del sonno: avvio, guasti del microfono, chiusure, errori.
 * Resta solo sul telefono (memoria privata dell'app) ed è visibile in Opzioni → Diagnostica.
 * Non contiene audio né dati del sonno, solo eventi con l'ora.
 */
object SleepLog {
    private const val MAX_LINES = 300
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALIAN)

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "sleep_log.txt")

    @Synchronized
    fun log(ctx: Context, message: String) {
        runCatching {
            val f = file(ctx)
            val lines = (if (f.exists()) f.readLines() else emptyList()) + "${fmt.format(Date())}  $message"
            f.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }

    @Synchronized
    fun read(ctx: Context): List<String> = runCatching { file(ctx).readLines() }.getOrDefault(emptyList())

    @Synchronized
    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }
}
