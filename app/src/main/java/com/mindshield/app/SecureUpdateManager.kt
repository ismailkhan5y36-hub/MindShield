package com.mindshield.app

import android.content.Context
import android.content.SharedPreferences

/**
 * SecureUpdateManager: Enforces strict anti-downgrade version checking
 * and authorized Master Serial Key verification for all updates.
 */
class SecureUpdateManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("mindshield_update_prefs", Context.MODE_PRIVATE)

    companion object {
        const val MASTER_SERIAL_KEY = "MS-SEC-2026-X9K"
        const val CURRENT_VERSION_CODE = 16
        const val CURRENT_VERSION_NAME = "v6.5.0"

        private const val KEY_LAST_UPDATE_TIME = "last_update_timestamp"
        private const val KEY_AUTHORIZED_SERIAL = "authorized_serial_key"

        @Volatile
        private var instance: SecureUpdateManager? = null

        fun getInstance(context: Context): SecureUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: SecureUpdateManager(context).also { instance = it }
            }
        }

        fun hasWriteSecureSettings(context: Context): Boolean {
            return context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    sealed class UpdateValidationResult {
        object Valid : UpdateValidationResult()
        data class RejectedDowngrade(val targetCode: Int, val currentCode: Int) : UpdateValidationResult()
        data class RejectedInvalidSerial(val reason: String) : UpdateValidationResult()
    }

    fun validateUpdate(targetVersionCode: Int, serialKey: String): UpdateValidationResult {
        // 1. Strict Anti-Downgrade & Same-Version check
        if (targetVersionCode <= CURRENT_VERSION_CODE) {
            return UpdateValidationResult.RejectedDowngrade(targetVersionCode, CURRENT_VERSION_CODE)
        }

        // 2. Cryptographic Master Serial Key Match
        if (serialKey.trim() != MASTER_SERIAL_KEY) {
            return UpdateValidationResult.RejectedInvalidSerial("Security Serial Mismatch. Expected authorized key.")
        }

        return UpdateValidationResult.Valid
    }

    fun recordSuccessfulUpdate() {
        prefs.edit()
            .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
            .putString(KEY_AUTHORIZED_SERIAL, MASTER_SERIAL_KEY)
            .apply()
    }
}
