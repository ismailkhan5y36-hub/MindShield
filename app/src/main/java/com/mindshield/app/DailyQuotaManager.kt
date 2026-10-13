package com.mindshield.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyQuotaManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mindshield_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DAILY_LIMIT_SECONDS = 3600 // 1 hour = 60 mins = 3600 secs
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_USED_SECONDS = "used_seconds"
        private const val KEY_BLOCKED_ATTEMPTS = "blocked_attempts"
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun checkAndResetDaily() {
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        if (today != lastDate) {
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_USED_SECONDS, 0)
                .apply()
        }
    }

    fun getUsedSeconds(): Int {
        checkAndResetDaily()
        return prefs.getInt(KEY_USED_SECONDS, 0)
    }

    fun addUsedSeconds(seconds: Int) {
        checkAndResetDaily()
        val current = prefs.getInt(KEY_USED_SECONDS, 0)
        val updated = current + seconds
        prefs.edit().putInt(KEY_USED_SECONDS, updated).apply()
    }

    fun isQuotaExhausted(): Boolean {
        return getUsedSeconds() >= DAILY_LIMIT_SECONDS
    }

    fun getRemainingSeconds(): Int {
        val remaining = DAILY_LIMIT_SECONDS - getUsedSeconds()
        return if (remaining > 0) remaining else 0
    }

    fun recordBadContentBlock() {
        val current = prefs.getInt(KEY_BLOCKED_ATTEMPTS, 0)
        prefs.edit().putInt(KEY_BLOCKED_ATTEMPTS, current + 1).apply()
    }

    fun getBadContentBlockCount(): Int {
        return prefs.getInt(KEY_BLOCKED_ATTEMPTS, 0)
    }
}
