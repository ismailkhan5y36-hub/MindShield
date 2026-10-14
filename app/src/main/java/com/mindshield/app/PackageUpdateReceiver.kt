package com.mindshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * PackageUpdateReceiver: Listens for ACTION_MY_PACKAGE_REPLACED.
 * Automatically self-heals Accessibility service upon update via WRITE_SECURE_SETTINGS,
 * re-starts the 24/7 Local DNS Firewall, and provides a 1-tap fallback recovery screen.
 */
class PackageUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // 1. Record update in SecureUpdateManager
        SecureUpdateManager.getInstance(context).recordSuccessfulUpdate()

        // 2. Re-arm Local DNS Firewall 24/7 automatically in background (only if VPN permission granted)
        try {
            if (android.net.VpnService.prepare(context) == null) {
                val dnsIntent = Intent(context, LocalDnsService::class.java).apply {
                    action = LocalDnsService.ACTION_START
                }
                ContextCompat.startForegroundService(context, dnsIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Attempt Programmatic Self-Healing of Accessibility Service
        val isAutoHealed = AccessibilityAutoHealer.healAccessibility(context)

        // 4. If not auto-healed, launch 1-Tap Recovery Screen and high-priority notification
        if (!isAutoHealed) {
            showUpdateRecoveryNotification(context)

            try {
                val recoveryIntent = Intent(context, UpdateRecoveryActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(recoveryIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateRecoveryNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mindshield_update_recovery"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MindShield Update Re-Confirmation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies user to re-confirm Accessibility after APK update"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            909,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("🛡️ MindShield Updated")
            .setContentText("Tap to verify Accessibility and ensure 24/7 active protection.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(9090, notification)
    }
}
