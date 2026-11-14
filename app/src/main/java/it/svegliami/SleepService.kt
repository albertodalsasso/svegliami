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
        val usingAccelerometer get() = instance?.accelMode == true
        /** Modalità della sessione in corso (null se non c'è). */
        val mode get() = instance?.trackingMode

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, SleepService::class.java).setAction(ACTION_START))

        /** Ferma e salva la sessione (stesso processo: chiamabile anche dalla sveglia). */
        fun stop() {
            instance?.finish()
        }

        /** Oltre questo vuoto un monitoraggio interrotto non viene ripreso: è un'altra notte. */
        private const val MAX_RESUME_GAP_MS = 2 * 3600_000L

        /**
         * Se un monitoraggio risulta ancora "in corso" ma il servizio non c'è, il sistema l'ha chiuso
         * all'improvviso: lo si registra per avvisare l'utente e si chiude la sessione.
         */
        fun checkInterrupted(ctx: Context) {
            if (isRunning) return
            val p = Prefs(ctx)
            val start = p.sleepActiveStart
            if (start == 0L) return
            p.sleepActiveStart = 0
            p.sleepInterruptedAt = SleepStore.load(ctx).firstOrNull { it.start == start }?.end ?: start
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
            // Un errore in un minuto non deve chiudere tutta la notte: si registra e si continua.
            runCatching { onMinute() }.onFailure {
                SleepLog.log(this@SleepService, "errore nel minuto: ${it.javaClass.simpleName}: ${it.message}")
            }
            if (instance === this@SleepService) handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        if (intent == null && instance == null) {
            // Riavvio da parte del sistema dopo una chiusura: riprende la notte, se è ancora in corso.
            val resumable = Prefs(this).sleepActiveStart.takeIf { it > 0 }
                ?.let { start -> SleepStore.load(this).firstOrNull { it.start == start } }
                ?.takeIf { System.currentTimeMillis() - it.end < MAX_RESUME_GAP_MS }
            if (resumable == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            // In background il microfono non è consentito: si riprende con l'accelerometro.
            trackingMode = resumable.trackingMode
            micMode = false
            accelMode = true
            if (!tryForeground()) {
                stopSelf()
                return START_NOT_STICKY
            }
            begin(resumable)
            return START_STICKY
        }
        if (instance == null) {
            val chosen = Prefs(this).sleepTrackingMode
            micMode = chosen.usesMicrophone && hasMicPermission(this)
            // Comodino senza permesso del microfono: si misura con l'accelerometro invece di
            // non misurare niente (la notte risulterebbe tutta "sonno profondo").
            trackingMode = if (chosen.usesMicrophone && !micMode) SleepTrackingMode.MATTRESS else chosen
            accelMode = !micMode
        }
        goForeground()
        if (instance == null) begin(null)
        // STICKY: se il sistema chiude il servizio, prova a farlo ripartire (vedi sopra).
        return START_STICKY
    }

    /** startForeground può essere vietato quando il servizio riparte in background. */
    private fun tryForeground(): Boolean = runCatching { goForeground() }.isSuccess

    private fun goForeground() {
        val notif = buildNotification()
        var type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        if (micMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    /** Aggiorna solo il testo della notifica, senza ripetere startForeground (che in background può essere vietato). */
    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Monitoraggio del sonno", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, NightActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, SleepService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("🌙 Monitoraggio del sonno attivo")
            .setContentText(when {
                micMode -> "Telefono sul comodino: elaborazione audio, nessuna registrazione"
                trackingMode.usesMicrophone -> "Microfono non disponibile: misuro i movimenti con l'accelerometro"
                else -> "Lascia il telefono sul materasso, vicino al cuscino"
            })
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Ferma", stop)
            .build()
    }

    /** Avvia una sessione nuova, oppure riprende [resume] riempiendo il vuoto della chiusura. */
    private fun begin(resume: SleepSession?) {
        instance = this
        scores.clear()
        movementScores.clear(); soundScores.clear(); snore.clear()
        samples = 0; moving = 0; last = null; smartWakeAt = 0
        if (resume == null) {
            startTime = System.currentTimeMillis()
        } else {
            startTime = resume.start
            smartWakeAt = resume.smartWakeAt
            scores += resume.scores
            movementScores += resume.scores.indices.map { resume.movementScores.getOrNull(it) }
            soundScores += resume.scores.indices.map { resume.soundScores.getOrNull(it) }
            snore += resume.scores.indices.map { resume.snore.getOrElse(it) { false } }
            // Minuti persi durante la chiusura: senza misure, con il valore medio degli ultimi minuti
            // (né "profondo" né "sveglio" inventati), così gli orari del grafico restano allineati.
            val missing = ((System.currentTimeMillis() - startTime) / 60_000L - scores.size).toInt().coerceAtLeast(0)
            val fill = scores.takeLast(5).takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
            repeat(missing) { scores += fill; movementScores += null; soundScores += null; snore += false }
        }
        Prefs(this).sleepActiveStart = startTime
        SleepLog.log(this, if (resume == null) "avvio (${trackingMode.name}, microfono=${micMode})"
            else "ripresa dopo una chiusura del sistema (${scores.size} min già registrati)")

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "svegliami:sleep")
            .apply { acquire(MAX_DURATION_MS) }
        recentFloors.clear()
        synchronized(micLevels) { micLevels.clear(); micLowRatios.clear() }
        if (accelMode) startAccelerometer()
        if (micMode) startMic()
        handler.postDelayed(tick, 60_000)
        handler.postDelayed({ finish() }, (startTime + MAX_DURATION_MS - System.currentTimeMillis()).coerceAtLeast(60_000))
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
                SleepLog.log(this, "microfono: impossibile aprirlo")
                handler.post { microphoneUnavailable() }
                return@Thread
            }
            val buf = ShortArray(CHUNK)
            var low = 0f
            var failed = false
            var errors = 0
            try {
                rec.startRecording()
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n < 0) {
                        // Errori passeggeri (alcuni telefoni sospendono il microfono per un attimo a
                        // schermo spento): si riprova. Si rinuncia solo dopo ~10 s di errori continui.
                        if (errors++ == 0) SleepLog.log(this, "microfono: errore di lettura $n")
                        if (errors >= 100) { failed = true; break }
                        Thread.sleep(100)
                        continue
                    }
                    if (errors > 0) { SleepLog.log(this, "microfono: ripreso dopo $errors errori"); errors = 0 }
                    if (n == 0) continue
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
            } catch (e: Exception) {
                SleepLog.log(this, "microfono: eccezione ${e.javaClass.simpleName}: ${e.message}")
                failed = true
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
            if (failed) handler.post { microphoneUnavailable() }
        }, "svegliami-mic").start()
    }

    /**
     * Il microfono si è fermato: da qui in poi misura l'accelerometro. La sessione resta
     * "comodino", così il russare e i punteggi audio dei minuti precedenti non vanno persi.
     */
    private fun microphoneUnavailable() {
        if (instance !== this) return
        SleepLog.log(this, "microfono non disponibile: passo all'accelerometro")
        micMode = false
        if (!accelMode) {
            accelMode = true
            startAccelerometer()
        }
        updateNotification()
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
            moving * 100f / samples
        else null
        val mic = if (micMode) micMinute() else null
        // Un solo sensore attivo per volta: il microfono, oppure l'accelerometro (scelto o di ripiego).
        val score = if (mic != null) mic.first.score else accel ?: 0f
        movementScores += accel
        soundScores += mic?.first?.score
        snore += mic?.first?.snoring ?: false
        scores += score
        samples = 0; moving = 0
        persist() // ogni minuto: se il sistema chiude l'app si perde al massimo un minuto
        if (scores.size % 30 == 0) SleepLog.log(this, "in corso: ${scores.size} min")
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
        // Sessioni di pochi minuti: di norma non si salvano (Buonanotte premuto per sbaglio).
        // In Opzioni si possono tenere tutte, per i test: basta un minuto registrato.
        val minMinutes = if (Prefs(this).sleepSaveShort) 1 else 10
        if (scores.size < minMinutes) return
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
        Prefs(this).sleepActiveStart = 0 // chiusura regolare: niente da segnalare
        SleepLog.log(this, "fine (${scores.size} min)")
        wakeLock?.let { if (it.isHeld) it.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        SleepLog.log(this, "app tolta dalle recenti")
        super.onTaskRemoved(rootIntent)
    }

    override fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_RUNNING_LOW) SleepLog.log(this, "memoria scarsa (livello $level)")
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        if (instance === this) SleepLog.log(this, "servizio chiuso dal sistema")
        finish()
        super.onDestroy()
    }
}
