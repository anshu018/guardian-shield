# Foreground Services Skill

# Load this skill for any task involving services, workers, or background processes.

---

## What This Skill Covers

Any agent working on LocationTrackingService, AppMonitorService,
EmergencySOSService, BootReceiver, or ServiceWatchdogWorker must
read this entire file before writing a single line.

---

## The Core Problem

Indian OEM ROMs (Xiaomi MIUI, Realme UI, Samsung One UI) have aggressive
battery optimizers that kill foreground services even when START_STICKY
is set. A standard Android foreground service WILL die on these devices.
We use a 3-layer survival strategy to counter this.

Layer 1: START_STICKY + onTaskRemoved AlarmManager restart
Layer 2: onDestroy AlarmManager restart
Layer 3: WorkManager watchdog every 15 minutes

All 3 layers must be implemented on every service. No exceptions.

---

## Service Base Template

Every service in the child app must follow this exact structure:

```kotlin
@AndroidEntryPoint
class YourService : Service() {

    // Always use SupervisorJob so one failed coroutine doesn't cancel others
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // start your work here inside serviceScope
        return START_STICKY // Layer 1: OS restarts this service if killed
    }

    // Layer 1 continued: called when user swipes app from recents
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart() // restart via AlarmManager
    }

    // Layer 2: called when service is killed by OS or OEM battery optimizer
    override fun onDestroy() {
        serviceScope.cancel() // clean up coroutines
        scheduleRestart()     // restart via AlarmManager
        super.onDestroy()
    }

    private fun scheduleRestart() {
        val restartIntent = Intent(applicationContext, YourService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Restart after 1 second — fast enough to not miss events
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000L,
            pendingIntent
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

---

## Notification Channel Setup

Must be created in Application class, not in the service itself.
Creating it in the service causes duplicate channel errors on restart.

```kotlin
// In GuardianShieldApp : Application()
fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,               // "guardian_shield_service"
            "Guardian Shield Active", // shown to user in settings
            NotificationManager.IMPORTANCE_LOW // LOW = no sound, no popup
        ).apply {
            description = "Keeps Guardian Shield running in background"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
```

Notification text must always say: "Guardian Shield Active 🛡️"
Never change this text — parents expect to see it.

---

## BootReceiver Pattern

```kotlin
@BroadcastReceiver(exported = false)
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // QUICKBOOT_POWERON is required for Xiaomi devices
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {
            startAllServices(context)
        }
    }

    private fun startAllServices(context: Context) {
        val services = listOf(
            LocationTrackingService::class.java,
            AppMonitorService::class.java,
            EmergencySOSService::class.java
        )
        services.forEach { serviceClass ->
            val intent = Intent(context, serviceClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
```

Manifest registration (both actions required):

```xml
<receiver
    android:name=".BootReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

---

## WorkManager Watchdog (Layer 3)

```kotlin
class ServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val servicesToCheck = listOf(
            LocationTrackingService::class.java,
            AppMonitorService::class.java,
            EmergencySOSService::class.java
        )
        servicesToCheck.forEach { serviceClass ->
            if (!isServiceRunning(serviceClass)) {
                val intent = Intent(applicationContext, serviceClass)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            }
        }
        return Result.success()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION") // only reliable way to check own services
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
}

// Schedule this in Application onCreate():
val watchdogRequest = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
    15, TimeUnit.MINUTES
)
    .setConstraints(Constraints.Builder().build())
    .build()

WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "service_watchdog",
    ExistingPeriodicWorkPolicy.KEEP, // don't restart timer if already scheduled
    watchdogRequest
)
```

---

## OEM-Specific Notes

Xiaomi (MIUI):

- Must request "Autostart" permission — guide user to Settings in setup wizard
- QUICKBOOT_POWERON action is mandatory
- Battery saver kills services within 5 minutes without autostart

Realme (Realme UI) / Oppo (ColorOS):

- Must guide user to disable battery optimization for the app
- Settings path: Settings → Battery → Battery Optimization → Guardian Shield → Don't optimize

Samsung (One UI):

- Must guide user to enable "Allow background activity" in app battery settings
- Settings path: Settings → Apps → Guardian Shield → Battery → Unrestricted

All three OEM-specific steps must appear in the setup wizard (L15).

---

## What to Never Do

- Never run background work without a foreground notification
- Never use JobScheduler alone — it gets deferred on OEM ROMs
- Never assume START_STICKY is enough on its own
- Never create notification channels inside a Service
- Never skip the QUICKBOOT_POWERON intent filter
- Never cancel serviceScope before calling super.onDestroy()
