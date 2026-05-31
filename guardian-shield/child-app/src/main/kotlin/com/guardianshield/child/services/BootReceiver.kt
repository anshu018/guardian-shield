package com.guardianshield.child.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.guardianshield.child.utils.ServiceHealthChecker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            startService(context, LocationTrackingService::class.java)
            startService(context, AppMonitorService::class.java)
            startService(context, EmergencySOSService::class.java)
            
            // Additional safety layer for aggressive battery savers
            ServiceHealthChecker.ensureServicesRunning(context)
        }
    }

    private fun startService(context: Context, serviceClass: Class<*>) {
        val serviceIntent = Intent(context, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

