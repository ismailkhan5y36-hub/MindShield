package com.mindshield.app

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale

class ColdFrictionActivity : AppCompatActivity() {

    private lateinit var tvColdTimer: TextView
    private lateinit var tvBreathingGuide: TextView
    private lateinit var btnColdExit: MaterialButton
    private var countDownTimer: CountDownTimer? = null

    companion object {
        const val FRICTION_DURATION_MS = 180 * 1000L // 3 Minutes (180 Seconds)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cold_friction)

        tvColdTimer = findViewById(R.id.tvColdTimer)
        tvBreathingGuide = findViewById(R.id.tvBreathingGuide)
        btnColdExit = findViewById(R.id.btnColdExit)

        btnColdExit.setOnClickListener {
            // Return safely to Android Home launcher
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        startColdTimer()
    }

    private fun startColdTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(FRICTION_DURATION_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                tvColdTimer.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                btnColdExit.text = String.format(Locale.US, "Lock Active (Wait %02d:%02d)", minutes, seconds)

                // Box Breathing Cycle (16-second cycle: 4s inhale, 4s hold, 4s exhale, 4s rest)
                val cycleStep = ((FRICTION_DURATION_MS - millisUntilFinished) / 1000) % 16
                when (cycleStep) {
                    in 0..3 -> tvBreathingGuide.text = "🌬️ Inhale Deeply (4s)..."
                    in 4..7 -> tvBreathingGuide.text = "⏸️ Hold Breath (4s)..."
                    in 8..11 -> tvBreathingGuide.text = "💨 Exhale Slowly (4s)..."
                    else -> tvBreathingGuide.text = "🌿 Rest & Relax (4s)..."
                }
            }

            override fun onFinish() {
                tvColdTimer.text = "00:00"
                tvBreathingGuide.text = "✅ Urge Dopamine Reset Complete!"
                btnColdExit.isEnabled = true
                btnColdExit.text = "Alhamdulillah, I Won! (Exit)"
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Strictly prevent exiting during the 3-minute cooling period
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
