package com.mindshield.app

import android.content.Context
import android.content.SharedPreferences
import com.mindshield.app.model.GoalItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProductivityManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mindshield_productivity_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GOALS = "saved_goals"
        private const val KEY_STREAK = "clean_streak_days"
        private const val KEY_LAST_STREAK_DATE = "last_streak_date"
        private const val KEY_FOCUS_SESSIONS = "focus_sessions_today"
        private const val KEY_LAST_FOCUS_DATE = "last_focus_date"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // --- Goals CRUD ---
    fun getGoals(): MutableList<GoalItem> {
        val raw = prefs.getString(KEY_GOALS, "") ?: ""
        if (raw.isEmpty()) {
            // Default pre-loaded starter goals
            return mutableListOf(
                GoalItem(1, "30 Min Parhai / Study", false, getTodayDate()),
                GoalItem(2, "Exercise / Walk", false, getTodayDate()),
                GoalItem(3, "Kitab / Quran Reading", false, getTodayDate())
            )
        }

        val list = mutableListOf<GoalItem>()
        val items = raw.split("|||")
        for (item in items) {
            val parts = item.split(":::")
            if (parts.size >= 3) {
                val id = parts[0].toLongOrNull() ?: System.currentTimeMillis()
                val title = parts[1]
                val completed = parts[2].toBoolean()
                val date = if (parts.size >= 4) parts[3] else getTodayDate()
                list.add(GoalItem(id, title, completed, date))
            }
        }
        return list
    }

    fun saveGoals(goals: List<GoalItem>) {
        val raw = goals.joinToString("|||") { "${it.id}:::${it.title}:::${it.isCompleted}:::${it.date}" }
        prefs.edit().putString(KEY_GOALS, raw).apply()
    }

    fun addGoal(title: String) {
        val current = getGoals()
        current.add(GoalItem(System.currentTimeMillis(), title, false, getTodayDate()))
        saveGoals(current)
    }

    fun toggleGoal(id: Long) {
        val current = getGoals()
        val item = current.find { it.id == id }
        if (item != null) {
            item.isCompleted = !item.isCompleted
            saveGoals(current)
        }
    }

    fun deleteGoal(id: Long) {
        val current = getGoals()
        current.removeAll { it.id == id }
        saveGoals(current)
    }

    // --- Streak Counter ---
    fun getStreakDays(): Int {
        checkAndAdvanceStreak()
        return prefs.getInt(KEY_STREAK, 1)
    }

    private fun checkAndAdvanceStreak() {
        val today = getTodayDate()
        val lastDate = prefs.getString(KEY_LAST_STREAK_DATE, "")
        if (lastDate.isEmpty()) {
            prefs.edit().putString(KEY_LAST_STREAK_DATE, today).putInt(KEY_STREAK, 1).apply()
        }
    }

    // --- Focus Sessions (Pomodoro) ---
    fun getFocusSessionsToday(): Int {
        val today = getTodayDate()
        val lastDate = prefs.getString(KEY_LAST_FOCUS_DATE, "")
        if (today != lastDate) {
            prefs.edit().putString(KEY_LAST_FOCUS_DATE, today).putInt(KEY_FOCUS_SESSIONS, 0).apply()
            return 0
        }
        return prefs.getInt(KEY_FOCUS_SESSIONS, 0)
    }

    fun incrementFocusSession() {
        val current = getFocusSessionsToday()
        prefs.edit()
            .putString(KEY_LAST_FOCUS_DATE, getTodayDate())
            .putInt(KEY_FOCUS_SESSIONS, current + 1)
            .apply()
    }

    // --- Reminders Setting ---
    fun isRemindersEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDERS_ENABLED, true)
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
    }
}
