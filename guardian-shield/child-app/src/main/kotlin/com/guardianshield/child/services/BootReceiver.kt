package com.guardianshield.child.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.utils.ServiceHealthChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var locationDataStore: LocationDataStore

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val isSetupComplete = runBlocking {
                try {
                    locationDataStore.isSetupCompleted()
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to check setup status from DataStore: ${e.message}")
                    false
                }
            }

            if (!isSetupComplete) {
                Log.d("BootReceiver", "Boot broadcast received, but setup is not complete. Services will not start.")
                return
            }

            Log.d("BootReceiver", "Boot broadcast received and setup is complete. Starting core background services...")
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


