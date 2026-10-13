package com.mindshield.app

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CoolingOffActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var btnHome: Button
    private lateinit var antiCheatManager: AntiCheatManager
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cooling_off)

        antiCheatManager = AntiCheatManager(this)
        tvTimer = findViewById(R.id.tvCoolingTimer)
        btnHome = findViewById(R.id.btnCoolingHome)

        btnHome.setOnClickListener {
            goHome()
        }

        startCountdown()
    }

    private fun startCountdown() {
        countDownTimer?.cancel()

        val remainingSeconds = antiCheatManager.getRemainingCoolingOffSeconds()
        if (remainingSeconds <= 0) {
            tvTimer.text = "00:00:00 (Unlocked)"
            return
        }

        countDownTimer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSecs = millisUntilFinished / 1000
                val hours = totalSecs / 3600
                val mins = (totalSecs % 3600) / 60
                val secs = totalSecs % 60
                tvTimer.text = String.format("%02d:%02d:%02d", hours, mins, secs)
            }

            override fun onFinish() {
                tvTimer.text = "00:00:00 (Unlocked)"
            }
        }.start()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
