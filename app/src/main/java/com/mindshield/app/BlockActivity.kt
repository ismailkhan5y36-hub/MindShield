package com.mindshield.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REASON = "extra_reason"
        const val REASON_QUOTA = "reason_quota"
        const val REASON_ADULT = "reason_adult"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        val tvTitle = findViewById<TextView>(R.id.tvBlockTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvBlockSubtitle)
        val tvDesc = findViewById<TextView>(R.id.tvBlockDescription)
        val btnGoHome = findViewById<Button>(R.id.btnGoHome)

        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_QUOTA

        if (reason == REASON_ADULT) {
            tvTitle.text = "CONTENT BLOCKED!"
            tvSubtitle.text = "MindShield: Focus & Protection Active"
            tvDesc.text = "Yeh content productive zehen ke liye theek nahi hai. Is waqt apne maqsad aur asal zindagi par dhyan dein."
        } else {
            tvTitle.text = "TIME'S UP!"
            tvSubtitle.text = "Aaj Ka 1 Ghanta Reels Quota Poora!"
            tvDesc.text = "Is se na 1 minute zyada, na kam. Apne waqt aur zehen ki qadar karein. Baqi quota kal raat 12:00 AM par reset hoga."
        }

        btnGoHome.setOnClickListener {
            // Direct to Android home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent simply pressing back to bypass, force to home
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
