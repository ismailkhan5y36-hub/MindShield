package com.mindshield.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class UpdateRecoveryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_recovery)

        // Step 1: Open App Info for Restricted Settings
        findViewById<MaterialButton>(R.id.btnOpenAppInfo)?.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open App Info automatically.", Toast.LENGTH_SHORT).show()
            }
        }

        // Step 2: Open Accessibility Settings
        findViewById<MaterialButton>(R.id.btnReconfirmAccessibility)?.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }

        // Copy ADB Command for Zero-Touch Mode
        findViewById<MaterialButton>(R.id.btnCopyAdbCommand)?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(
                "ADB Command",
                "adb shell pm grant com.mindshield.app android.permission.WRITE_SECURE_SETTINGS"
            )
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "📋 ADB command copied! Run once on PC or Shizuku.", Toast.LENGTH_LONG).show()
        }

        findViewById<MaterialButton>(R.id.btnDismissUpdate)?.setOnClickListener {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // If accessibility is already active, dismiss automatically and go to main
        if (isAccessibilityServiceEnabled(this)) {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${ShieldAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(expected)
    }
}
