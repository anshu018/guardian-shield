package com.guardianshield.child.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.guardianshield.child.services.LocationTrackingService
import com.guardianshield.child.services.AppMonitorService
import com.guardianshield.child.services.EmergencySOSService

object ServiceHealthChecker {
    
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) 
            as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
    
    fun ensureServicesRunning(context: Context) {
        if (!isServiceRunning(context, LocationTrackingService::class.java)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationTrackingService::class.java)
            )
        }
        if (!isServiceRunning(context, AppMonitorService::class.java)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AppMonitorService::class.java)
            )
        }
        if (!isServiceRunning(context, EmergencySOSService::class.java)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, EmergencySOSService::class.java)
            )
        }
    }
    
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/" +
            "com.guardianshield.child.services.GuardianAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedService)
    }
}
