package com.guardianshield.child.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telephony.SmsManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.guardianshield.child.admin.GuardianDeviceAdminReceiver
import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.data.remote.dto.RemoteCommandDto
import com.guardianshield.child.data.remote.dto.SosEventDto
import com.guardianshield.child.utils.Constants
import com.guardianshield.child.utils.CryptoUtils
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class EmergencySOSService : Service() {

    @Inject lateinit var supabaseClient: SupabaseClient
    @Inject lateinit var locationDataStore: LocationDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeChannel: RealtimeChannel? = null
    private var mediaPlayer: MediaPlayer? = null
    private var overlayView: View? = null

    private var isSosTriggeredState = false

    override fun onCreate() {
        super.onCreate()
        startStationaryMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID + 2, buildNotification())
        
        if (intent?.action == ACTION_TRIGGER_MANUAL_SOS) {
            triggerSOS(isManual = true)
        }
        
        // Start commands subscription
        startCommandSubscription()

        return START_STICKY
    }

    private fun startStationaryMonitor() {
        serviceScope.launch {
            while (isActive) {
                val startTime = locationDataStore.getStationaryStartTime()
                if (startTime > 0) {
                    val durationMs = System.currentTimeMillis() - startTime
                    if (durationMs > 30 * 60 * 1000L) { // 30 minutes
                        if (!isSosTriggeredState) {
                            triggerSOS(isManual = false)
                        }
                    }
                }
                delay(60_000L) // check every minute
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerSOS(isManual: Boolean) {
        if (isSosTriggeredState) return
        isSosTriggeredState = true

        serviceScope.launch {
            val childId = locationDataStore.getLastKnownLocation()?.childId ?: "device_child_1"
            val lastLoc = locationDataStore.getLastKnownLocation()
            val lat = lastLoc?.lat ?: 0.0
            val lng = lastLoc?.lng ?: 0.0

            val hasInternet = isInternetAvailable()

            if (hasInternet) {
                val sosDto = SosEventDto(
                    childId = childId,
                    lat = lat,
                    lng = lng,
                    active = true
                )
                try {
                    supabaseClient.postgrest.from(Constants.SUPABASE_TABLE_SOS).insert(sosDto)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to SMS if insert fails
                    sendSmsFallback(childId, lat, lng)
                }
            } else {
                sendSmsFallback(childId, lat, lng)
            }

            // Notify LocationTrackingService to speed up location tracking to 5s updates
            notifyLocationTrackingServiceSosState(true)
        }
    }

    private fun notifyLocationTrackingServiceSosState(active: Boolean) {
        val updateIntent = Intent(this, LocationTrackingService::class.java).apply {
            putExtra("EXTRA_SOS_ACTIVE", active)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(updateIntent)
        } else {
            startService(updateIntent)
        }
    }

    private fun sendSmsFallback(childId: String, lat: Double, lng: Double) {
        serviceScope.launch {
            val parentPhone = locationDataStore.getParentPhone() ?: "+919876543210"
            val childName = locationDataStore.getChildName() ?: "Child"

            val mapsUrl = "https://maps.google.com/?q=$lat,$lng"
            val rawMessage = "SOS! $childName needs help. Location: $mapsUrl"

            // Encrypt using CryptoUtils
            val encryptedPayload = CryptoUtils.encrypt(rawMessage)
            val smsMessage = "GS_SOS_SECURE: $encryptedPayload"

            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                smsManager.sendTextMessage(parentPhone, null, smsMessage, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startCommandSubscription() {
        serviceScope.launch {
            try {
                val childId = locationDataStore.getLastKnownLocation()?.childId ?: "device_child_1"

                val channel = supabaseClient.realtime.channel("remote_commands_channel")
                realtimeChannel = channel

                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = Constants.SUPABASE_TABLE_COMMANDS
                }

                channel.subscribe()

                changeFlow.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            handleIncomingCommand(action.record)
                        }
                        is PostgresAction.Update -> {
                            handleIncomingCommand(action.record)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                delay(10_000L)
                startCommandSubscription()
            }
        }
    }

    private fun handleIncomingCommand(record: JsonObject) {
        serviceScope.launch {
            try {
                val commandDto = Json.decodeFromJsonElement<RemoteCommandDto>(record)
                if (!commandDto.executed) {
                    val currentChildId = locationDataStore.getLastKnownLocation()?.childId ?: "device_child_1"
                    if (commandDto.childId == currentChildId) {
                        val commandId = commandDto.id ?: return@launch
                        val success = executeCommand(commandDto.command, commandDto.payload)

                        if (success) {
                            val currentUtcString = Instant.now().toString()
                            supabaseClient.postgrest.from(Constants.SUPABASE_TABLE_COMMANDS)
                                .update(mapOf("is_executed" to true, "executed_at" to currentUtcString)) {
                                    filter {
                                        eq("id", commandId)
                                    }
                                }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun executeCommand(commandType: String, payload: String?): Boolean {
        return try {
            when (commandType) {
                "LOCK" -> {
                    GuardianDeviceAdminReceiver.lockDevice(applicationContext)
                    true
                }
                "ALARM" -> {
                    val action = try {
                        if (payload != null && payload.startsWith("{")) {
                            Json.decodeFromString<Map<String, String>>(payload)["action"]
                        } else {
                            payload
                        }
                    } catch (e: Exception) {
                        payload
                    }
                    
                    when (action) {
                        "start" -> playAlarm(applicationContext)
                        "stop" -> {
                            stopAlarm()
                            mediaPlayer?.release()
                            mediaPlayer = null
                        }
                        else -> {
                            if (action != null && (action.equals("stop", ignoreCase = true) || action.equals("false", ignoreCase = true))) {
                                stopAlarm()
                                mediaPlayer?.release()
                                mediaPlayer = null
                            } else {
                                playAlarm(applicationContext)
                            }
                        }
                    }
                    true
                }
                "MESSAGE" -> {
                    val message = payload ?: "Attention!"
                    showMessageOverlay(applicationContext, message)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun playAlarm(context: Context) {
        try {
            stopAlarm() // Stop previous loop if any
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showMessageOverlay(context: Context, message: String) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        Handler(Looper.getMainLooper()).post {
            try {
                removeOverlay(context)

                val container = FrameLayout(context).apply {
                    setBackgroundColor(Color.parseColor("#E60D0D1D")) // Semi-transparent dark blue-gray
                }

                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(48, 48, 48, 48)
                    setBackgroundColor(Color.parseColor("#1F1F3D")) // Sleek deep card
                }

                val titleText = TextView(context).apply {
                    text = "PARENTAL MESSAGE"
                    setTextColor(Color.parseColor("#FF5A5A")) // Danger Red Accent
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 32)
                }

                val bodyText = TextView(context).apply {
                    text = message
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 48)
                }

                val dismissButton = Button(context).apply {
                    text = "Acknowledge"
                    setBackgroundColor(Color.parseColor("#3B82F6")) // Blue Accent
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        removeOverlay(context)
                    }
                }

                card.addView(titleText)
                card.addView(bodyText)
                card.addView(dismissButton)

                val cardParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ).apply {
                    setMargins(64, 0, 64, 0)
                }

                container.addView(card, cardParams)
                windowManager.addView(container, params)
                overlayView = container
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun removeOverlay(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
            overlayView = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    override fun onDestroy() {
        serviceScope.launch {
            try {
                realtimeChannel?.unsubscribe()
            } catch (e: Exception) {
                // Ignore
            }
        }
        stopAlarm()
        removeOverlay(applicationContext)
        serviceScope.cancel()
        scheduleRestart()
        super.onDestroy()
    }

    private fun scheduleRestart() {
        val intent = Intent(applicationContext, EmergencySOSService::class.java)
        val pi = PendingIntent.getService(
            applicationContext, Constants.RESTART_REQUEST_CODE + 2, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000L, pi)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
        .setContentTitle("System Service")
        .setContentText("Monitoring system health")
        .setSmallIcon(android.R.drawable.ic_menu_manage)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setOngoing(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_TRIGGER_MANUAL_SOS = "com.guardianshield.child.action.TRIGGER_MANUAL_SOS"
    }
}
