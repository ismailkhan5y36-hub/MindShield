package com.mindshield.app

import java.util.Calendar

object RiskTimeEngine {

    enum class RiskLevel {
        NORMAL,
        HIGH_VULNERABILITY
    }

    /**
     * Checks if current time falls within user-specified vulnerability windows:
     * 1. Afternoon Urge Window: 2:00 PM (14:00) to 5:00 PM (17:00) daily
     * 2. Night Vulnerability Window: 9:00 PM (21:00) to 6:00 AM (06:00) daily
     * 3. Sunday Full Lockdown: All 24 hours on Sunday
     */
    fun getCurrentRiskLevel(currentTimeMillis: Long = System.currentTimeMillis()): RiskLevel {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
        }

        // Window 3: Sunday all day (24 hours)
        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            return RiskLevel.HIGH_VULNERABILITY
        }

        val hour = calendar.get(Calendar.HOUR_OF_DAY) // 0..23

        // Window 1: 2:00 PM (14:00) to 5:00 PM (17:00)
        // hour is 14 (2 PM), 15 (3 PM), 16 (4 PM) -> ends at 17:00 (5 PM)
        if (hour in 14..16) {
            return RiskLevel.HIGH_VULNERABILITY
        }

        // Window 2: 9:00 PM (21:00) to 6:00 AM (06:00)
        // 21 (9 PM), 22 (10 PM), 23 (11 PM) OR 0..5 (midnight to 6 AM)
        if (hour >= 21 || hour < 6) {
            return RiskLevel.HIGH_VULNERABILITY
        }

        return RiskLevel.NORMAL
    }

    fun isHighRiskWindow(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return getCurrentRiskLevel(currentTimeMillis) == RiskLevel.HIGH_VULNERABILITY
    }

    fun getRiskWindowDescription(): String {
        return "Active Windows: 2:00 PM - 5:00 PM | 9:00 PM - 6:00 AM | Sundays (Full Day)"
    }
}
