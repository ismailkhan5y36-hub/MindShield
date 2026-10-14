package com.mindshield.app

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * CamouflageManager: Controls app disguise in Android launcher.
 * Switches between default "MindShield" and disguised "Notes" launcher alias.
 */
class CamouflageManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("mindshield_camouflage_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CAMOUFLAGE = "is_camouflage_enabled"
        private const val KEY_PIN = "security_pin"
        private const val KEY_SAVED_NOTES = "saved_scratchpad_notes"

        const val DEFAULT_PIN = "1234"

        const val ALIAS_DEFAULT = "com.mindshield.app.MainActivityAlias"
        const val ALIAS_NOTES = "com.mindshield.app.NotesAlias"

        @Volatile
        private var instance: CamouflageManager? = null

        fun getInstance(context: Context): CamouflageManager {
            return instance ?: synchronized(this) {
                instance ?: CamouflageManager(context).also { instance = it }
            }
        }
    }

    var isCamouflageEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAMOUFLAGE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CAMOUFLAGE, value).apply()
            applyLauncherAlias(value)
        }

    var securityPin: String
        get() = prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        set(value) = prefs.edit().putString(KEY_PIN, value.trim()).apply()

    var savedNotes: String
        get() = prefs.getString(KEY_SAVED_NOTES, "Meeting notes:\n- Review productivity goals\n- Read book chapters\n- Walk in the evening") ?: ""
        set(value) = prefs.edit().putString(KEY_SAVED_NOTES, value).apply()

    fun verifyPin(input: String): Boolean {
        return input.trim() == securityPin
    }

    private fun applyLauncherAlias(useNotesDisguise: Boolean) {
        val pm = appContext.packageManager
        val defaultAlias = ComponentName(appContext, ALIAS_DEFAULT)
        val notesAlias = ComponentName(appContext, ALIAS_NOTES)

        try {
            if (useNotesDisguise) {
                // Enable Notes disguise, disable default
                pm.setComponentEnabledSetting(
                    notesAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    defaultAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                // Enable default, disable Notes disguise
                pm.setComponentEnabledSetting(
                    defaultAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    notesAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
