package com.mindshield.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var quotaManager: DailyQuotaManager

    private lateinit var viewStatusDot: View
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvRemainingLabel: TextView
    private lateinit var tvUsedToday: TextView
    private lateinit var pbQuota: ProgressBar

    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnBattery: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        quotaManager = DailyQuotaManager(this)

        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    private fun initViews() {
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvStatusDesc = findViewById(R.id.tvStatusDesc)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        tvRemainingLabel = findViewById(R.id.tvRemainingLabel)
        tvUsedToday = findViewById(R.id.tvUsedToday)
        pbQuota = findViewById(R.id.pbQuota)

        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnBattery = findViewById(R.id.btnBattery)
    }

    private fun setupListeners() {
        // 1. Accessibility Setting Button
        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // 2. Overlay Permission Button
        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }

        // 3. Battery Saver Exemption Button (Tecno Spark 30 Pro / HiOS)
        btnBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general battery settings
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(fallback)
                }
            }
        }
    }

    private fun updateDashboard() {
        val isServiceOn = isAccessibilityServiceEnabled()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        // Update Status Card
        if (isServiceOn) {
            viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.success))
            tvServiceStatus.text = "Shield Active & Protecting"
            tvStatusDesc.text = "All Reels, Shorts aur galat content real-time monitor ho rahe hain."
            btnAccessibility.text = "✓ Accessibility Service Enabled"
            btnAccessibility.strokeColor = ContextCompat.getColorStateList(this, R.color.success)
        } else {
            viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.danger))
            tvServiceStatus.text = "Protection Inactive"
            tvStatusDesc.text = "Neeche button par click karke 'MindShield' service ko on karein."
            btnAccessibility.text = "1. Turn On Accessibility Service"
        }

        if (hasOverlay) {
            btnOverlay.text = "✓ Display Over Apps Allowed"
            btnOverlay.strokeColor = ContextCompat.getColorStateList(this, R.color.success)
        } else {
            btnOverlay.text = "2. Allow Display Over Other Apps"
        }

        // Check Battery Exemption
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm.isIgnoringBatteryOptimizations(packageName)) {
            btnBattery.text = "✓ HiOS Battery Optimization Ignored"
            btnBattery.strokeColor = ContextCompat.getColorStateList(this, R.color.success)
        } else {
            btnBattery.text = "3. Tecno/HiOS Battery Exemption"
        }

        // Update Quota Time
        val remainingSecs = quotaManager.getRemainingSeconds()
        val usedSecs = quotaManager.getUsedSeconds()

        val remainingMins = remainingSecs / 60
        val remainingSecondsInMin = remainingSecs % 60
        tvRemainingTime.text = String.format("%02d:%02d", remainingMins, remainingSecondsInMin)

        pbQuota.progress = usedSecs
        val usedMins = usedSecs / 60
        tvUsedToday.text = "Used Today: $usedMins / 60 mins"

        if (remainingSecs <= 0) {
            tvRemainingLabel.text = "DAILY QUOTA EXHAUSTED! BLOCKED."
            tvRemainingLabel.setTextColor(ContextCompat.getColor(this, R.color.danger))
        } else {
            tvRemainingLabel.text = "Remaining (out of 60 mins)"
            tvRemainingLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "${packageName}/${ShieldAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(expectedComponentName) ||
                enabledServices.contains(ShieldAccessibilityService::class.java.simpleName)
    }
}
