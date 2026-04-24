package com.guardianshield.child.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.guardianshield.child.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class ScreenCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID + 3, buildNotification())
        // TODO: Start MediaProjection + WebRTC pipeline — L9
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        scheduleRestart()
        super.onDestroy()
    }

    private fun scheduleRestart() {
        val intent = Intent(applicationContext, ScreenCaptureService::class.java)
        val pi = PendingIntent.getService(
            applicationContext, Constants.RESTART_REQUEST_CODE + 3, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000L, pi)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
        .setContentTitle("Screen Cast") // Visible when media projection is active
        .setContentText("Casting")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null
}
