package com.guardianshield.child.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        
        // Enforce uninstall restriction immediately when Device Admin is enabled
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Warning shown when trying to deactivate Device Admin
        return "Disabling this will terminate active child physical protection and notify parents immediately."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Trigger high-priority actions/alerts to parent in L7/L13
    }

    companion object {
        /**
         * Triggers the DevicePolicyManager to lock the screen instantly.
         * Enforces strict child safety command loops.
         */
        fun lockDevice(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                try {
                    dpm.lockNow()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
