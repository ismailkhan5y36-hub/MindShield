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

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val antiCheatManager = AntiCheatManager(context)

            // Check if accessibility service is running
            if (!isAccessibilityServiceEnabled(context)) {
                showAccessibilityAlertNotification(context)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = "${context.packageName}/${ShieldAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(expectedComponentName) ||
                enabledServices.contains(ShieldAccessibilityService::class.java.simpleName)
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
