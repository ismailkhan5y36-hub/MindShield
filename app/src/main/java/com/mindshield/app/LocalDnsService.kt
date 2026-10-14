package com.mindshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * LocalDnsService:
 * 24/7 Persistent Background Protection Guard & Accessibility Watchdog.
 * Maintains zero-touch survival without interfering with native Android 14 WiFi/Carrier DNS,
 * guaranteeing 100% full-speed, uninterrupted internet for all apps.
 */
class LocalDnsService : Service() {

    companion object {
        const val ACTION_START = "com.mindshield.app.START_DNS"
        const val ACTION_STOP = "com.mindshield.app.STOP_DNS"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "mindshield_dns_guard"
        private const val TAG = "LocalDnsService"
    }

    @Volatile
    private var isRunning = false
    private var watchdogThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DomainDatabase.init(this)
        AccessibilityAutoHealer.healAccessibility(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopGuardService()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        startWatchdog()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "MindShield 24/7 Protection Guard",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Maintains 24/7 active protection and auto-healing watchdog"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("🛡️ MindShield Protection Active")
                .setContentText("24/7 Guard active. Internet speed & apps 100% normal.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
                } catch (e: Exception) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startWatchdog() {
        if (isRunning) return
        isRunning = true

        watchdogThread?.interrupt()
        watchdogThread = Thread({
            Log.i(TAG, "Accessibility watchdog thread started")
            while (isRunning) {
                try {
                    Thread.sleep(15000)
                    if (!isRunning) break

                    if (!AccessibilityAutoHealer.isAccessibilityEnabled(this)) {
                        Log.w(TAG, "Watchdog detected Accessibility was inactive! Auto-healing via WRITE_SECURE_SETTINGS...")
                        AccessibilityAutoHealer.healAccessibility(this)
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Watchdog loop error: ${e.message}")
                }
            }
            Log.i(TAG, "Accessibility watchdog thread stopped")
        }, "MindShield-Watchdog").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopGuardService() {
        isRunning = false
        watchdogThread?.interrupt()
        watchdogThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGuardService()
    }
}
