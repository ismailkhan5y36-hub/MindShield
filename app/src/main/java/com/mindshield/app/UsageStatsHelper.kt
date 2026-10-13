package com.mindshield.app

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.mindshield.app.model.AppUsageInfo
import java.util.Calendar

object UsageStatsHelper {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getStartOfDayMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getDailyUsageStats(context: Context): Pair<Long, List<AppUsageInfo>> {
        if (!hasUsageStatsPermission(context)) {
            return Pair(0L, emptyList())
        }

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startTime = getStartOfDayMillis()
        val endTime = System.currentTimeMillis()

        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val packageManager = context.packageManager

        var totalScreenTimeMs = 0L
        val appList = mutableListOf<AppUsageInfo>()

        // Ignored system and launcher packages for cleaner display
        val ignoredPackages = setOf(
            "com.android.systemui",
            "com.transsion.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            context.packageName
        )

        val validStats = statsMap.values.filter {
            it.totalTimeInForeground > 60_000L && !ignoredPackages.contains(it.packageName)
        }

        for (stat in validStats) {
            totalScreenTimeMs += stat.totalTimeInForeground
        }

        for (stat in validStats) {
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(stat.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                stat.packageName.substringAfterLast('.')
            }

            val formatted = formatDuration(stat.totalTimeInForeground)
            val percentage = if (totalScreenTimeMs > 0) {
                ((stat.totalTimeInForeground * 100) / totalScreenTimeMs).toInt()
            } else 0

            appList.add(
                AppUsageInfo(
                    packageName = stat.packageName,
                    appName = appName,
                    usageTimeMillis = stat.totalTimeInForeground,
                    usageFormatted = formatted,
                    percentage = percentage
                )
            )
        }

        // Sort descending by usage time
        val sortedList = appList.sortedByDescending { it.usageTimeMillis }.take(8)
        return Pair(totalScreenTimeMs, sortedList)
    }

    fun formatDuration(millis: Long): String {
        val totalMins = millis / (1000 * 60)
        val hours = totalMins / 60
        val mins = totalMins % 60
        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m"
        }
    }
}
