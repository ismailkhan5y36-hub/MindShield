package com.mindshield.app.model

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long,
    val usageFormatted: String,
    val percentage: Int
)
