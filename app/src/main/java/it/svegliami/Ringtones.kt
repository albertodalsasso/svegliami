package it.svegliami

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat

object Ringtones {
    fun defaultAlarmUri(ctx: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** Suoneria effettiva: quella scelta, oppure la predefinita. */
    fun currentUri(ctx: Context): Uri? =
        Prefs(ctx).ringtoneUri.takeIf { it.isNotEmpty() }?.let(Uri::parse) ?: defaultAlarmUri(ctx)

    fun title(ctx: Context): String {
        val chosen = Prefs(ctx).ringtoneUri
        if (chosen.isEmpty()) return "Predefinita del telefono"
        return runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(chosen))?.getTitle(ctx) }
            .getOrNull() ?: "Suoneria personalizzata"
    }

    fun preview(ctx: Context): Ringtone? = runCatching {
        RingtoneManager.getRingtone(ctx, currentUri(ctx))?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            play()
        }
    }.getOrNull()

    /** I file audio aggiunti dall'utente stanno nella memoria condivisa e richiedono un permesso di lettura. */
    fun storagePermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun needsStoragePermission(ctx: Context): Boolean {
        val uri = Prefs(ctx).ringtoneUri
        if (!uri.startsWith("content://media/external")) return false
        return ContextCompat.checkSelfPermission(ctx, storagePermission()) != PackageManager.PERMISSION_GRANTED
    }
}
