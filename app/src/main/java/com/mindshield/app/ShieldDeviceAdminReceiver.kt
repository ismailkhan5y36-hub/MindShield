package com.mindshield.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class ShieldDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Warning shown by Android OS when attempting to deactivate Device Admin
        return "⚠️ MindShield Hardware Lock is active. Deactivation requires a 2-Hour Cooling-Off delay to protect you during urges."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
