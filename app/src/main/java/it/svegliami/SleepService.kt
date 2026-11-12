package it.svegliami

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Monitoraggio notturno: registra i movimenti dall'accelerometro (telefono sul materasso)
 * e, nella finestra della sveglia intelligente, fa suonare la sveglia al primo sonno leggero.
 */
class SleepService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "it.svegliami.SLEEP_START"
        const val ACTION_STOP = "it.svegliami.SLEEP_STOP"
        private const val CHANNEL_ID = "sleep"
        private const val NOTIF_ID = 43
        private const val MAX_DURATION_MS = 14 * 3600_000L

        @Volatile
        private var instance: SleepService? = null

        val isRunning get() = instance != null
        val startedAt get() = instance?.startTime ?: 0L

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, SleepService::class.java).setAction(ACTION_START))

        /** Ferma e salva la sessione (stesso processo: chiamabile anche dalla sveglia). */
        fun stop() {
            instance?.finish()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null

    private var startTime = 0L
    private val scores = mutableListOf<Float>()
    private var samples = 0
    private var moving = 0
    private var last: FloatArray? = null
    private var smartWakeAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            onMinute()
            if (instance === this@SleepService) handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        goForeground()
        if (instance == null) begin()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Monitoraggio del sonno", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, SleepService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("🌙 Monitoraggio del sonno attivo")
            .setContentText("Lascia il telefono sul materasso, vicino al cuscino")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Ferma", stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    private fun begin() {
        instance = this
        startTime = System.currentTimeMillis()
        scores.clear()
        samples = 0; moving = 0; last = null; smartWakeAt = 0

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "svegliami:sleep")
            .apply { acquire(MAX_DURATION_MS) }
        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        handler.postDelayed(tick, 60_000)
        handler.postDelayed({ finish() }, MAX_DURATION_MS)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        last?.let { p ->
            val dx = v[0] - p[0]; val dy = v[1] - p[1]; val dz = v[2] - p[2]
            samples++
            if (sqrt(dx * dx + dy * dy + dz * dz) > SleepAnalyzer.MOVING_DELTA) moving++
        }
        last = v.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onMinute() {
        val score = if (samples == 0) 0f else moving * 100f / samples
        scores += score
        samples = 0; moving = 0
        if (scores.size % 10 == 0) persist() // salvataggio periodico, nel caso il processo venga chiuso
        checkSmartWake(score)
    }

    /** Se siamo nella finestra di una sveglia intelligente e c'è movimento, suona ora. */
    private fun checkSmartWake(score: Float) {
        if (!SleepAnalyzer.shouldWake(score)) return
        val p = Prefs(this)
        val now = System.currentTimeMillis()
        val next = p.alarms.filter { it.enabled && it.smartWindowMin > 0 }.mapNotNull { a ->
            val st = p.state(a.id)
            val occ = st.currentOccurrence
            if (occ == 0L) null else Triple(a, occ, if (st.earlyAt > 0) st.earlyAt else occ)
        }.minByOrNull { it.third } ?: return
        val (a, occ, target) = next
        if (now >= target - a.smartWindowMin * 60_000L && now < target - 30_000L) {
            smartWakeAt = now
            AlarmFire.ring(this, a.id, AlarmFire.KIND_SMART, occ)
        }
    }

    private fun persist() {
        if (scores.size < 10) return // sessioni di pochi minuti: non le salva
        SleepStore.upsert(this, SleepSession(startTime, System.currentTimeMillis(), scores.toList(), smartWakeAt))
    }

    private fun finish() {
        if (instance !== this) return
        instance = null
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(this)
        persist()
        wakeLock?.let { if (it.isHeld) it.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        finish()
        super.onDestroy()
    }
}
