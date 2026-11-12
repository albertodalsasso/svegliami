package it.svegliami

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import it.svegliami.sensors.SensorFusion
import it.svegliami.sensors.SensorSignal
import it.svegliami.storage.SleepRoomRepository
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Monitoraggio notturno: registra i movimenti dall'accelerometro (telefono sul materasso)
 * o dal volume del microfono (telefono sul comodino)
 * e, nella finestra della sveglia intelligente, fa suonare la sveglia al primo sonno leggero.
 */
class SleepService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "it.svegliami.SLEEP_START"
        const val ACTION_STOP = "it.svegliami.SLEEP_STOP"
        private const val CHANNEL_ID = "sleep"
        private const val NOTIF_ID = 43
        private const val MAX_DURATION_MS = 14 * 3600_000L
        private const val SAMPLE_RATE = 16_000
        /** 100 ms di audio per lettura → 600 livelli al minuto. */
        private const val CHUNK = SAMPLE_RATE / 10
        /** Filtro passa-basso a un polo a ~800 Hz: separa i suoni gravi (russare) da quelli acuti (fruscio). */
        private val LOW_PASS_ALPHA = (1 - kotlin.math.exp(-2 * Math.PI * 800 / SAMPLE_RATE)).toFloat()
        /** Minuti su cui si stima il rumore di fondo della stanza. */
        private const val FLOOR_MINUTES = 10

        fun hasMicPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        @Volatile
        private var instance: SleepService? = null

        val isRunning get() = instance != null
        val startedAt get() = instance?.startTime ?: 0L
        val usingMic get() = instance?.micMode == true

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
    private val movementScores = mutableListOf<Float?>()
    private val soundScores = mutableListOf<Float?>()
    private var samples = 0
    private var moving = 0
    private var last: FloatArray? = null
    private var smartWakeAt = 0L

    // Modalità microfono: solo il livello (dB) di ogni blocco da 100 ms, mai l'audio.
    private var trackingMode = SleepTrackingMode.MATTRESS
    private var micMode = false
    private var accelMode = false
    @Volatile private var recording = false
    private val micLevels = ArrayList<Float>()   // protetti dal lock di micLevels
    private val micLowRatios = ArrayList<Float>()
    private val snore = mutableListOf<Boolean>()
    private val recentFloors = ArrayDeque<Float>()

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
        if (instance == null) {
            trackingMode = Prefs(this).sleepTrackingMode
            micMode = trackingMode.usesMicrophone && hasMicPermission(this)
            accelMode = trackingMode.usesAccelerometer
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
            .setContentText(when (trackingMode) {
                SleepTrackingMode.NIGHTSTAND -> "Telefono sul comodino: elaborazione audio, nessuna registrazione"
                SleepTrackingMode.COMBINED -> "Accelerometro e microfono attivi; audio non salvato"
                SleepTrackingMode.MATTRESS -> "Lascia il telefono sul materasso, vicino al cuscino"
            })
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Ferma", stop)
            .build()
        var type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        if (micMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    private fun begin() {
        instance = this
        startTime = System.currentTimeMillis()
        scores.clear()
        movementScores.clear(); soundScores.clear()
        samples = 0; moving = 0; last = null; smartWakeAt = 0

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "svegliami:sleep")
            .apply { acquire(MAX_DURATION_MS) }
        recentFloors.clear()
        synchronized(micLevels) { micLevels.clear(); micLowRatios.clear() }
        snore.clear()
        if (accelMode) startAccelerometer()
        if (micMode) startMic()
        handler.postDelayed(tick, 60_000)
        handler.postDelayed({ finish() }, MAX_DURATION_MS)
    }

    private fun startAccelerometer() {
        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            accelMode = sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) == true
        }
    }

    @SuppressLint("MissingPermission") // verificato in onStartCommand
    private fun createRecorder(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return null
        // VOICE_RECOGNITION di solito non ha il controllo automatico del volume: livelli più confrontabili.
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val rec = runCatching {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, CHUNK * 4))
            }.getOrNull()
            if (rec?.state == AudioRecord.STATE_INITIALIZED) return rec
            rec?.release()
        }
        return null
    }

    private fun startMic() {
        recording = true
        Thread({
            val rec = createRecorder()
            if (rec == null) {
                // In combinata conserva l'accelerometro; in comodino ripiega sul materasso.
                handler.post {
                    if (instance === this) {
                        micMode = false
                        if (!accelMode) {
                            trackingMode = SleepTrackingMode.MATTRESS
                            accelMode = true
                            startAccelerometer()
                        }
                    }
                }
                return@Thread
            }
            val buf = ShortArray(CHUNK)
            var low = 0f
            try {
                rec.startRecording()
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    var lowSum = 0.0
                    for (i in 0 until n) {
                        val x = buf[i].toFloat()
                        low += LOW_PASS_ALPHA * (x - low)
                        sum += x.toDouble() * x
                        lowSum += low.toDouble() * low
                    }
                    val db = (20 * log10(sqrt(sum / n) + 1.0)).toFloat()
                    val ratio = if (sum > 0) (lowSum / sum).toFloat().coerceIn(0f, 1f) else 0f
                    synchronized(micLevels) { micLevels.add(db); micLowRatios.add(ratio) }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }, "svegliami-mic").start()
    }

    /** Punteggio del minuto dal microfono, rispetto al rumore di fondo degli ultimi minuti. */
    private fun micMinute(): Pair<MicMinute, Float> {
        val (levels, ratios) = synchronized(micLevels) {
            (ArrayList(micLevels) to ArrayList(micLowRatios)).also { micLevels.clear(); micLowRatios.clear() }
        }
        if (levels.isEmpty()) return MicMinute(0f, false) to 0f
        recentFloors.addLast(SleepAnalyzer.percentile(levels, 0.1f))
        while (recentFloors.size > FLOOR_MINUTES) recentFloors.removeFirst()
        val baseline = SleepAnalyzer.percentile(recentFloors.toList(), 0.5f)
        return SleepAnalyzer.analyzeMicMinute(levels, ratios, baseline) to (levels.size / 600f).coerceIn(0f, 1f)
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
        val accel = if (accelMode && samples > 0)
            SensorSignal(moving * 100f / samples, (samples / 120f).coerceIn(0f, 1f))
        else null
        val mic = if (micMode) micMinute() else null
        val score = when (trackingMode) {
            SleepTrackingMode.MATTRESS -> accel?.activityScore ?: 0f
            SleepTrackingMode.NIGHTSTAND -> mic?.first?.score ?: 0f
            SleepTrackingMode.COMBINED -> SensorFusion.combine(
                accel,
                mic?.let { SensorSignal(it.first.score, it.second) },
            ).activityScore
        }
        movementScores += accel?.activityScore
        soundScores += mic?.first?.score
        snore += mic?.first?.snoring ?: false
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
        val session = SleepSession(
            start = startTime,
            end = System.currentTimeMillis(),
            scores = scores.toList(),
            smartWakeAt = smartWakeAt,
            mic = trackingMode.usesMicrophone,
            snore = snore.toList(),
            trackingMode = trackingMode,
            movementScores = movementScores.toList(),
            soundScores = soundScores.toList(),
        )
        SleepStore.upsert(this, session)
        SleepRoomRepository.mirror(this, session)
    }

    private fun finish() {
        if (instance !== this) return
        instance = null
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(this)
        recording = false
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
