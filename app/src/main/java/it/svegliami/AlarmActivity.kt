package it.svegliami

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Schermata a tutto schermo mostrata quando suona la sveglia (anche a schermo bloccato). */
class AlarmActivity : AppCompatActivity() {
    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)
        SystemBars.apply(this)

        findViewById<TextView>(R.id.alarmTime).text = Scheduler.fmtTime(System.currentTimeMillis())
        val message = intent.getStringExtra(AlarmService.EXTRA_MESSAGE) ?: ""
        findViewById<TextView>(R.id.alarmMessage).text = message
        val rain = "☔" in message
        findViewById<View>(R.id.alarmRoot).setBackgroundResource(if (rain) R.drawable.bg_alarm_rain else R.drawable.bg_alarm_dawn)
        findViewById<TextView>(R.id.alarmIcon).text = when {
            "🌙" in message -> "🌙"
            rain -> "☔"
            else -> "☀️"
        }
        pulse(findViewById(R.id.pulseRing))
        findViewById<Button>(R.id.btnSnooze).text = "Posticipa ${Scheduler.SNOOZE_MIN} min"
        findViewById<Button>(R.id.btnSnooze).setOnClickListener { send(AlarmService.ACTION_SNOOZE) }
        findViewById<Button>(R.id.btnDismiss).setOnClickListener { send(AlarmService.ACTION_DISMISS) }

        ContextCompat.registerReceiver(
            this, stoppedReceiver, IntentFilter(AlarmService.ACTION_STOPPED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Anello che pulsa attorno all'orario. */
    private fun pulse(ring: View) {
        fun anim(prop: String, vararg v: Float) = ObjectAnimator.ofFloat(ring, prop, *v).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        AnimatorSet().apply {
            playTogether(anim("scaleX", 0.85f, 1.05f), anim("scaleY", 0.85f, 1.05f), anim("alpha", 1f, 0.35f))
            start()
        }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
        finish()
    }

    override fun onDestroy() {
        unregisterReceiver(stoppedReceiver)
        super.onDestroy()
    }
}
