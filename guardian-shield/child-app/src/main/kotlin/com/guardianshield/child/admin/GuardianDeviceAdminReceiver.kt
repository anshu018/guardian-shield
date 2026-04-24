package com.guardianshield.child.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // TODO: Log admin enabled -> Supabase
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // TODO: Return warning message, trigger SOS or alert parent
        return "Disabling this will stop child protection."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // TODO: Trigger high-priority alert to parent
    }
}
