package com.mindshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            // 1. Programmatically auto-heal Accessibility Service via WRITE_SECURE_SETTINGS
            val isHealed = AccessibilityAutoHealer.healAccessibility(context)
            Log.i(TAG, "Accessibility auto-heal on boot result: $isHealed")

            // 2. Start 24/7 Protection Guard Service
            try {
                val guardIntent = Intent(context, LocalDnsService::class.java).apply {
                    this.action = LocalDnsService.ACTION_START
                }
                ContextCompat.startForegroundService(context, guardIntent)
                Log.i(TAG, "LocalDnsService guard started successfully on boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LocalDnsService on boot: ${e.message}")
            }

            // 3. Fallback alert ONLY if auto-healing failed and accessibility is not running
            if (!AccessibilityAutoHealer.isAccessibilityEnabled(context)) {
                showAccessibilityAlertNotification(context)
            }
        }
    }

    private fun showAccessibilityAlertNotification(context: Context) {
        val channelId = "mindshield_boot_guard"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MindShield Protection Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts if protection service was stopped after restart"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("🛡️ MindShield Guard Inactive")
            .setContentText("Phone restart hua hai. Tap karke MindShield Accessibility ON karein!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
