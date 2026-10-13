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
        const val REASON_DISTRACTION = "reason_distraction"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        val tvTitle = findViewById<TextView>(R.id.tvBlockTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvBlockSubtitle)
        val tvDesc = findViewById<TextView>(R.id.tvBlockDescription)
        val btnGoHome = findViewById<Button>(R.id.btnGoHome)

        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_QUOTA

        if (reason == REASON_DISTRACTION) {
            tvTitle.text = getString(R.string.block_distraction_title)
            tvSubtitle.text = getString(R.string.block_distraction_subtitle)
            tvDesc.text = getString(R.string.block_distraction_desc)
        } else {
            tvTitle.text = getString(R.string.block_quota_title)
            tvSubtitle.text = getString(R.string.block_quota_subtitle)
            tvDesc.text = getString(R.string.block_quota_desc)
        }

        btnGoHome.setOnClickListener {
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
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
