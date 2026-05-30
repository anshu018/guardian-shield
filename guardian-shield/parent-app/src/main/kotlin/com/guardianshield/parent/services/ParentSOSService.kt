package com.guardianshield.parent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guardianshield.parent.domain.repository.ParentRepository
import com.guardianshield.parent.ui.sos.SOSAlertActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ParentSOSService : Service() {

    @Inject
    lateinit var repository: ParentRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_ID_MONITOR = "sos_monitor_channel"
        const val CHANNEL_ID_ALERT = "sos_alert"
        const val NOTIFICATION_ID_MONITOR = 2001
        const val NOTIFICATION_ID_ALERT = 2002
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground service with persistent monitor notification
        val monitorNotification = buildMonitorNotification()
        startForeground(NOTIFICATION_ID_MONITOR, monitorNotification)

        // Start listening to SOS events
        startSosMonitoring()

        return START_STICKY
    }

    private fun startSosMonitoring() {
        serviceScope.launch {
            repository.observeSosEvents().collectLatest { event ->
                if (event.active) {
                    showCriticalSosAlert(event.id, event.childId, event.lat, event.lng)
                } else {
                    // Cancel active emergency alert notification if active is set to false
                    notificationManager.cancel(NOTIFICATION_ID_ALERT)
                }
            }
        }
    }

    private fun showCriticalSosAlert(eventId: String, childId: String, lat: Double, lng: Double) {
        val intent = Intent(this, SOSAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_SOS_EVENT_ID", eventId)
            putExtra("EXTRA_CHILD_ID", childId)
            putExtra("EXTRA_LATITUDE", lat)
            putExtra("EXTRA_LONGITUDE", lng)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // High priority alarm notification that overrides DND (fullScreenIntent)
        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("CRITICAL CHILD EMERGENCY!")
            .setContentText("Your child has triggered an SOS alert. Tap to view location!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // Lockscreen override
            .setOngoing(true) // Non-dismissible
            .setAutoCancel(false) // Cannot be swiped away
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT, alertNotification)
    }

    private fun buildMonitorNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_MONITOR)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Guardian Shield Protection")
            .setContentText("Monitoring child safety in the background...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Monitor channel (Low priority, silent)
            val monitorChannel = NotificationChannel(
                CHANNEL_ID_MONITOR,
                "Background Protection Status",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Shows that Guardian Shield is running in the background."
                setShowBadge(false)
            }

            // SOS Alert channel (High priority, bypassing DND, loud sound)
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT,
                "Child Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical SOS notifications that override Silent and DND modes."
                enableVibration(true)
                setBypassDnd(true)
            }

            notificationManager.createNotificationChannel(monitorChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
