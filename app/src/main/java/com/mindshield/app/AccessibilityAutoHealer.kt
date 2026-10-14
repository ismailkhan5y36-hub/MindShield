package com.mindshield.app

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * AccessibilityAutoHealer:
 * Programmatically restores and maintains ShieldAccessibilityService using WRITE_SECURE_SETTINGS.
 * Guarantees zero-touch survival after device reboot, battery saver resets, or HiOS background kills.
 */
object AccessibilityAutoHealer {

    private const val TAG = "AccessibilityAutoHealer"

    fun getExpectedServiceName(context: Context): String {
        return "${context.packageName}/${ShieldAccessibilityService::class.java.canonicalName}"
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            val expected = getExpectedServiceName(context)
            val enabledServices = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val isEnabled = Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1

            isEnabled && (enabledServices.contains(expected) || enabledServices.contains(ShieldAccessibilityService::class.java.simpleName))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility status: ${e.message}")
            false
        }
    }

    fun healAccessibility(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            val expected = getExpectedServiceName(context)
            val currentServices = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            val newServices = if (currentServices.isBlank()) {
                expected
            } else if (!currentServices.contains(expected) && !currentServices.contains(ShieldAccessibilityService::class.java.simpleName)) {
                "$currentServices:$expected"
            } else {
                currentServices
            }

            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "Successfully auto-healed accessibility service: $newServices")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS not granted, cannot auto-heal accessibility: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error auto-healing accessibility: ${e.message}")
            false
        }
    }
}
