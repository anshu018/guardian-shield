package com.guardianshield.child.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.domain.models.ChildLocation
import com.guardianshield.child.domain.repository.LocationRepository
import com.guardianshield.child.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service(), LocationListener {

    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var locationDataStore: LocationDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager

    private var currentInterval = Constants.LOCATION_INTERVAL_NORMAL
    private var lastLocationsList = mutableListOf<Location>()
    private var isStationaryMode = false
    private var isSosActive = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTrackingParameters()
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID, buildNotification())
        intent?.let {
            if (it.hasExtra("EXTRA_SOS_ACTIVE")) {
                isSosActive = it.getBooleanExtra("EXTRA_SOS_ACTIVE", false)
            }
        }
        updateTrackingParameters()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            locationManager.removeUpdates(this)

            val batteryPct = getBatteryPercentage()
            val useBalancedAccuracyOnly = batteryPct <= 15 && !isSosActive

            if (!useBalancedAccuracyOnly && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    currentInterval,
                    0f,
                    this
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    currentInterval,
                    0f,
                    this
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        serviceScope.launch {
            val childId = locationDataStore.getChildId()
            if (childId.isNullOrEmpty()) {
                android.util.Log.e("LocationTrackingService", "Location upload skipped: childId is null or empty in DataStore.")
                return@launch
            }
            val batteryPct = getBatteryPercentage()

            val childLocation = ChildLocation(
                childId = childId,
                lat = location.latitude,
                lng = location.longitude,
                battery = batteryPct,
                accuracy = location.accuracy,
                timestamp = location.time
            )

            detectStationaryState(location)
            locationRepository.uploadLocation(childLocation)
        }
    }

    private fun detectStationaryState(newLocation: Location) {
        lastLocationsList.add(newLocation)
        if (lastLocationsList.size > 3) {
            lastLocationsList.removeAt(0)
        }

        if (lastLocationsList.size == 3) {
            val loc1 = lastLocationsList[0]
            val loc2 = lastLocationsList[1]
            val loc3 = lastLocationsList[2]

            val dist1 = loc1.distanceTo(loc2)
            val dist2 = loc2.distanceTo(loc3)
            val dist3 = loc1.distanceTo(loc3)

            val isStationary = dist1 <= Constants.STATIONARY_THRESHOLD_METERS &&
                    dist2 <= Constants.STATIONARY_THRESHOLD_METERS &&
                    dist3 <= Constants.STATIONARY_THRESHOLD_METERS
            if (isStationary && !isStationaryMode) {
                isStationaryMode = true
                serviceScope.launch {
                    if (locationDataStore.getStationaryStartTime() == 0L) {
                        locationDataStore.saveStationaryStartTime(System.currentTimeMillis())
                    }
                }
                updateTrackingParameters()
            } else if (!isStationary && isStationaryMode) {
                isStationaryMode = false
                serviceScope.launch {
                    locationDataStore.saveStationaryStartTime(0L)
                }
                updateTrackingParameters()
            }
        }
    }

    private fun updateTrackingParameters() {
        val batteryPct = getBatteryPercentage()
        val desiredInterval = when {
            isSosActive -> Constants.LOCATION_INTERVAL_SOS
            batteryPct <= 15 -> 45_000L
            isStationaryMode -> Constants.LOCATION_INTERVAL_STATIONARY
            else -> Constants.LOCATION_INTERVAL_NORMAL
        }

        if (currentInterval != desiredInterval) {
            currentInterval = desiredInterval
            startLocationUpdates()
        }
    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 100
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            // Ignore
        }
        serviceScope.cancel()
        scheduleRestart()
        super.onDestroy()
    }

    private fun scheduleRestart() {
        val intent = Intent(applicationContext, LocationTrackingService::class.java)
        val pi = PendingIntent.getService(
            applicationContext, Constants.RESTART_REQUEST_CODE, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000L, pi)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
        .setContentTitle("System Service")
        .setContentText("Running core system processes")
        .setSmallIcon(android.R.drawable.ic_menu_manage)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setOngoing(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null
}
