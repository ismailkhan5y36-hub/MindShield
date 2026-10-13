package com.mindshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "mindshield_reminders_channel"
        const val NOTIFICATION_ID = 2001
    }

    private val reminderMessages = listOf(
        "Paani piyo aur ankhon ko aaraam do! Hydration zaroori hai.",
        "Apne aaj ke goals check karein. Waqt qeemti hai!",
        "Kamar seedhi karein aur 3 gehri saansein lein. Focus on your goals.",
        "Dopamine distraction se bachein. Kisi zaroori kaam par dhyan dein."
    )

    override fun onReceive(context: Context, intent: Intent) {
        val manager = ProductivityManager(context)
        if (!manager.isRemindersEnabled()) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MindShield Hourly Nudges",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic focus & hydration reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val randomMsg = reminderMessages.random()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("MindShield Focus Nudge")
            .setContentText(randomMsg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(randomMsg))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
