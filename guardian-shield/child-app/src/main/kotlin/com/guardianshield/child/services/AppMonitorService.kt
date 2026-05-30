package com.guardianshield.child.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.data.remote.dto.RemoteCommandDto
import com.guardianshield.child.domain.repository.AppUsageRepository
import com.guardianshield.child.ui.block.BlockActivity
import com.guardianshield.child.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

@AndroidEntryPoint
class AppMonitorService : Service() {


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var appUsageRepository: AppUsageRepository
    @Inject lateinit var dataStore: LocationDataStore
    @Inject lateinit var supabaseClient: SupabaseClient

    private var lastActivePackage: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID + 1, buildNotification())
        startPolling()
        startRealtimeSubscription()
        return START_STICKY
    }

    private fun startPolling() {
        serviceScope.launch {
            while (true) {
                try {
                    val foregroundPackage = getForegroundPackageName()
                    if (foregroundPackage != null) {
                        val blockedApps = appUsageRepository.getBlockedApps()
                        if (blockedApps.contains(foregroundPackage)) {
                            val blockIntent = Intent(applicationContext, BlockActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            startActivity(blockIntent)
                        } else if (foregroundPackage != lastActivePackage) {
                            if (!isIgnoredPackage(foregroundPackage)) {
                                lastActivePackage = foregroundPackage
                                val appName = getAppNameFromPackage(foregroundPackage)
                                appUsageRepository.uploadAppUsage(foregroundPackage, appName)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(30000L)
            }
        }
    }

    private fun getForegroundPackageName(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val time = System.currentTimeMillis()

        // 1. Event query approach
        val events = usm.queryEvents(time - 60000L, time)
        val event = UsageEvents.Event()
        var lastForegroundApp: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundApp = event.packageName
            }
        }
        if (lastForegroundApp != null) return lastForegroundApp

        // 2. Query UsageStats fallback
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 10 * 60 * 1000L, time)
        if (!stats.isNullOrEmpty()) {
            var lastUsed: UsageStats? = null
            for (stat in stats) {
                if (lastUsed == null || stat.lastTimeUsed > lastUsed.lastTimeUsed) {
                    lastUsed = stat
                }
            }
            return lastUsed?.packageName
        }
        return null
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        if (packageName == "com.guardianshield.child" || packageName == "com.android.systemui") return true
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val launcherPackage = resolveInfo?.activityInfo?.packageName
        return packageName == launcherPackage
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun startRealtimeSubscription() {
        serviceScope.launch {
            try {
                val childIdFromData = dataStore.getLastKnownLocation()?.childId ?: ""
                if (childIdFromData.isEmpty()) return@launch

                val channel = supabaseClient.realtime.channel("remote_commands_monitor")
                val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "remote_commands"
                }

                launch {
                    changes.collect { action ->
                        when (action) {
                            is PostgresAction.Insert -> {
                                val commandDto = Json.decodeFromJsonElement<RemoteCommandDto>(action.record)
                                if (commandDto.childId == childIdFromData && commandDto.command == "BLOCK_APP") {
                                    handleBlockCommand(commandDto)
                                }
                            }
                            is PostgresAction.Update -> {
                                val commandDto = Json.decodeFromJsonElement<RemoteCommandDto>(action.record)
                                if (commandDto.childId == childIdFromData && commandDto.command == "BLOCK_APP") {
                                    handleBlockCommand(commandDto)
                                }
                            }
                            else -> {}
                        }
                    }
                }
                channel.subscribe()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleBlockCommand(commandDto: RemoteCommandDto) {
        serviceScope.launch {
            try {
                val payload = commandDto.payload
                if (!payload.isNullOrEmpty()) {
                    val packagesToBlock = if (payload.startsWith("[")) {
                        Json.decodeFromString<List<String>>(payload)
                    } else {
                        listOf(payload)
                    }
                    appUsageRepository.saveBlockedApps(packagesToBlock)

                    val updatedCommand = commandDto.copy(executed = true, executedAt = getCurrentUtcString())
                    supabaseClient.postgrest.from("remote_commands")
                        .update(updatedCommand) {
                            filter {
                                eq("id", commandDto.id!!)
                            }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getCurrentUtcString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
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
        val intent = Intent(applicationContext, AppMonitorService::class.java)
        val pi = PendingIntent.getService(
            applicationContext, Constants.RESTART_REQUEST_CODE + 1, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000L, pi)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
        .setContentTitle("System Service")
        .setContentText("Running")
        .setSmallIcon(android.R.drawable.ic_menu_manage)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setOngoing(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null
}
