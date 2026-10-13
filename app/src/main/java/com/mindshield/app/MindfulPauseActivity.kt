package com.mindshield.app

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MindfulPauseActivity : AppCompatActivity() {

    private lateinit var tvCountdown: TextView
    private lateinit var btnHome: Button
    private lateinit var btnContinue: Button
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mindful_pause)

        tvCountdown = findViewById(R.id.tvPauseCountdown)
        btnHome = findViewById(R.id.btnPauseHome)
        btnContinue = findViewById(R.id.btnPauseContinue)

        btnHome.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        btnContinue.setOnClickListener {
            finish() // Allows user back into the app after conscious reflection
        }

        start5SecondTimer()
    }

    private fun start5SecondTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(5000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000) + 1
                tvCountdown.text = "${secs}s"
                btnContinue.text = "Wait ${secs}s to Continue..."
            }

            override fun onFinish() {
                tvCountdown.text = "✓"
                btnContinue.isEnabled = true
                btnContinue.text = "Theek Hai, Aage Barho (Proceed)"
                btnContinue.setTextColor(ContextCompat.getColor(this@MindfulPauseActivity, R.color.text_primary))
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
