package com.mindshield.app

import android.content.Context
import android.content.SharedPreferences

class AntiCheatManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mindshield_anticheat_prefs", Context.MODE_PRIVATE)

    companion object {
        const val COOLING_OFF_DURATION_MS = 2 * 60 * 60 * 1000L // 2 Hours (120 Mins)
        const val MAINTENANCE_WINDOW_MS = 10 * 60 * 1000L // 10 Minutes window

        private const val KEY_ANTI_CHEAT_ENABLED = "anticheat_enabled"
        private const val KEY_COOLING_OFF_END_TIME = "cooling_off_end_time"
    }

    // Enabled by default as requested by user
    fun isAntiCheatEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANTI_CHEAT_ENABLED, true)
    }

    fun setAntiCheatEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANTI_CHEAT_ENABLED, enabled).apply()
    }

    fun triggerCoolingOffIfIdle() {
        val currentEndTime = prefs.getLong(KEY_COOLING_OFF_END_TIME, 0L)
        val now = System.currentTimeMillis()
        if (now > currentEndTime + MAINTENANCE_WINDOW_MS) {
            // Start fresh 2-hour cooling period
            val newEndTime = now + COOLING_OFF_DURATION_MS
            prefs.edit().putLong(KEY_COOLING_OFF_END_TIME, newEndTime).apply()
        }
    }

    fun isCoolingOffActive(): Boolean {
        if (!isAntiCheatEnabled()) return false
        val endTime = prefs.getLong(KEY_COOLING_OFF_END_TIME, 0L)
        val now = System.currentTimeMillis()
        return now < endTime
    }

    fun isMaintenanceWindowOpen(): Boolean {
        if (!isAntiCheatEnabled()) return true
        val endTime = prefs.getLong(KEY_COOLING_OFF_END_TIME, 0L)
        val now = System.currentTimeMillis()
        return now in endTime..(endTime + MAINTENANCE_WINDOW_MS)
    }

    fun getRemainingCoolingOffSeconds(): Long {
        val endTime = prefs.getLong(KEY_COOLING_OFF_END_TIME, 0L)
        val now = System.currentTimeMillis()
        val remaining = (endTime - now) / 1000
        return if (remaining > 0) remaining else 0
    }
}
