package com.mindshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class VictoryScorecardReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "mindshield_scorecard_channel"
        const val NOTIFICATION_ID = 3001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val quotaManager = DailyQuotaManager(context)
        val productivityManager = ProductivityManager(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MindShield Daily Victory Scorecard",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily 9:30 PM productivity summary"
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

        val usedMins = quotaManager.getUsedSeconds() / 60
        val isClean = usedMins < 60
        val streak = productivityManager.getStreakDays()

        val title = if (isClean) {
            "🏆 Daily Victory: Focus Score 94%"
        } else {
            "📊 Daily Summary: MindShield Report"
        }

        val message = if (isClean) {
            "Alhamdulillah! Aaj reels sirf $usedMins/60 min use hue. Day $streak streak barkaraar hai. Zabardast self-discipline!"
        } else {
            "Aaj reels quota mukammal hua. Kal subah naya aaghaz karein aur apne targets par focus rakhein!"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
