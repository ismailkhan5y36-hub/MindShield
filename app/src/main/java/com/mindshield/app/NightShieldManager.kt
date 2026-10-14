package com.mindshield.app

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * NightShieldManager: Controls late-night bedtime focus protection (e.g. 11:00 PM to 6:00 AM).
 * Prevents late-night mindless scrolling in browsers and entertainment apps.
 */
class NightShieldManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("mindshield_night_shield_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENABLED = "night_shield_enabled"
        private const val KEY_START_HOUR = "night_start_hour"
        private const val KEY_START_MIN = "night_start_min"
        private const val KEY_END_HOUR = "night_end_hour"
        private const val KEY_END_MIN = "night_end_min"

        @Volatile
        private var instance: NightShieldManager? = null

        fun getInstance(context: Context): NightShieldManager {
            return instance ?: synchronized(this) {
                instance ?: NightShieldManager(context).also { instance = it }
            }
        }
    }

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var startHour: Int
        get() = prefs.getInt(KEY_START_HOUR, 23) // 11:00 PM
        set(value) = prefs.edit().putInt(KEY_START_HOUR, value).apply()

    var startMinute: Int
        get() = prefs.getInt(KEY_START_MIN, 0)
        set(value) = prefs.edit().putInt(KEY_START_MIN, value).apply()

    var endHour: Int
        get() = prefs.getInt(KEY_END_HOUR, 6) // 6:00 AM
        set(value) = prefs.edit().putInt(KEY_END_HOUR, value).apply()

    var endMinute: Int
        get() = prefs.getInt(KEY_END_MIN, 0)
        set(value) = prefs.edit().putInt(KEY_END_MIN, value).apply()

    fun isBedtimeActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!isEnabled) return false

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes < endMinutes) {
            // Same-day window (e.g., 01:00 to 05:00)
            currentMinutes in startMinutes until endMinutes
        } else {
            // Overnight window (e.g., 23:00 to 06:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }
}
