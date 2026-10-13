package com.mindshield.app

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MindResetActivity : AppCompatActivity() {

    private lateinit var tvPhase: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvQuote: TextView
    private lateinit var btnDone: Button

    private var currentPhase = 0 // 0: Inhale (4s), 1: Hold (7s), 2: Exhale (8s)
    private var timer: CountDownTimer? = null

    private val realityQuotes = listOf(
        "Yeh distraction urge sirf 2 minute ki hai. Agar aapne control kiya, toh poora din pur-etimad guzrega.",
        "5 second ka maza, ya poore din ka pachtawa? Choice aapke haath mein hai.",
        "Apne asal maqsad ko yaad karein. Aap is se kahin zyada qawi hain.",
        "Dopamine crash se bachein. Asal sukoon apne targets poore karne mein hai."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mind_reset)

        tvPhase = findViewById(R.id.tvResetPhase)
        tvTimer = findViewById(R.id.tvResetTimer)
        tvQuote = findViewById(R.id.tvRealityQuote)
        btnDone = findViewById(R.id.btnDoneReset)

        tvQuote.text = realityQuotes.random()

        btnDone.setOnClickListener {
            finish()
        }

        startBreathingCycle()
    }

    private fun startBreathingCycle() {
        val durationSeconds = when (currentPhase) {
            0 -> 4 // Inhale
            1 -> 7 // Hold
            else -> 8 // Exhale
        }

        val phaseText = when (currentPhase) {
            0 -> "Gehri Saans Lein (Inhale)"
            1 -> "Saans Rok Kar Rakhein (Hold)"
            else -> "Aahista Saans Chhodein (Exhale)"
        }

        tvPhase.text = phaseText

        timer?.cancel()
        timer = object : CountDownTimer((durationSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000) + 1
                tvTimer.text = "${sec}s"
            }

            override fun onFinish() {
                currentPhase = (currentPhase + 1) % 3
                if (currentPhase == 0) {
                    tvQuote.text = realityQuotes.random()
                }
                startBreathingCycle()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
