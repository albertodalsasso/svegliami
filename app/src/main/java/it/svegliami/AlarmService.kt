package it.svegliami

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Servizio in primo piano che fa suonare e vibrare la sveglia. */
class AlarmService : Service() {
    companion object {
        const val ACTION_START = "it.svegliami.START"
        const val ACTION_DISMISS = "it.svegliami.DISMISS"
        const val ACTION_SNOOZE = "it.svegliami.SNOOZE"
        const val ACTION_STOPPED = "it.svegliami.ALARM_STOPPED"
        const val EXTRA_MESSAGE = "message"
        const val CHANNEL_ID = "alarm"
        private const val NOTIF_ID = 42
        private const val AUTO_STOP_MS = 10 * 60_000L

        fun start(ctx: Context, message: String) {
            val i = Intent(ctx, AlarmService::class.java).setAction(ACTION_START).putExtra(EXTRA_MESSAGE, message)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun ensureChannel(ctx: Context) {
            val ch = NotificationChannel(CHANNEL_ID, "Sveglia", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // il suono lo gestisce il servizio
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarm(intent.getStringExtra(EXTRA_MESSAGE) ?: "Sveglia")
            ACTION_SNOOZE -> {
                Scheduler.scheduleSnooze(this)
                stopAlarm()
            }
            else -> stopAlarm()
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(message: String) {
        ensureChannel(this)
        stopSound()

        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).putExtra(EXTRA_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, AlarmService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Svegliami · ${Scheduler.fmtTime(System.currentTimeMillis())}")
            .setContentText(message.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Posticipa ${Scheduler.SNOOZE_MIN} min", action(ACTION_SNOOZE, 1))
            .addAction(0, "Ferma", action(ACTION_DISMISS, 2))
            .build()

        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)

        playSound()
        vibrate()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopAlarm() }, AUTO_STOP_MS)
    }

    private fun playSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0), attrs)
    }

    private fun stopSound() {
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrator?.cancel()
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        super.onDestroy()
    }
}
