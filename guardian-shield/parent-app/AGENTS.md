# Parent App — Agent Rules

# This file is read by every agent working inside parent-app/.

# Always read root AGENTS.md first — this file extends it, never contradicts it.

---

## Purpose of This App

This is the parent's dashboard — the control center for Guardian Shield.
It receives live data from the child's phone and lets the parent take action.
It must feel fast, trustworthy, and calm — even during an emergency.
Every screen exists to give the parent visibility and control.

Package: com.guardianshield.parent
App label: "Guardian Shield"
Min SDK: 26
Target SDK: 35

---

## Supabase Auth (v3 API — use this, never the old gotrue API)

```kotlin
// Send OTP to parent's phone number
suspend fun sendOtp(phone: String): Result<Unit> {
    return try {
        supabaseClient.auth.signInWith(OTP) {
            this.phone = phone  // E.164 format: +91XXXXXXXXXX
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

fun isLoggedIn(): Boolean =
    supabaseClient.auth.currentSessionOrNull() != null

fun getCurrentUserId(): String? =
    supabaseClient.auth.currentSessionOrNull()?.user?.id
```

---

## Realtime Data Rules

The parent app is a Realtime-first app.
Every piece of data that changes on the child's phone must
arrive on the parent's screen without the parent refreshing.

Three Realtime subscriptions must run at all times after login:

### 1. child_location subscription

- Table: child_location, filter: child_id = current child
- On every INSERT: update map marker position immediately
- Update battery %, accuracy, and last-seen timestamp in UI
- If no update received in 30 seconds: show "Last seen X minutes ago"
- Cache last known location in DataStore for offline display

### 2. sos_events subscription

- Table: sos_events, filter: child_id = current child, active = true
- On INSERT: immediately launch SOSAlertActivity as full-screen
- SOSAlertActivity must use FLAG_SHOW_WHEN_LOCKED + FLAG_TURN_SCREEN_ON
  so it appears even if parent's phone is locked
- Play loud alarm on parent's phone via AudioManager STREAM_ALARM
- Never dismiss SOS alert automatically — parent must tap "I'm on my way"

### 3. remote_commands subscription

- Table: remote_commands, filter: child_id = current child
- On INSERT with executed = false: show "Command sent, waiting..." in UI
- On UPDATE with executed = true: show "Command confirmed ✓" in UI
- Timeout after 30 seconds with no confirmation: show "No response — child may be offline"

All subscriptions must:

- Be started in a ViewModel using viewModelScope
- Use callbackFlow with awaitClose to clean up channels
- Reconnect automatically if Supabase Realtime disconnects
- Log disconnection and reconnection events for debugging

---

## Screen Structure

### Dashboard (Home Screen)

- Live OSMDroid map centered on child's last known location
- Map updates position every time a new child_location row arrives
- Movement trail: last 10 positions shown as a faded polyline
- Status bar below map: battery %, current app open, online/offline pill
- Floating SOS button — parent can manually trigger SOS check
- If child is offline > 5 minutes: show yellow warning banner

### Live Screen Tab

- WebRTC video stream from child's phone
- Uses SurfaceViewRenderer from stream-webrtc-android
- Connects to Railway signaling server with role = "parent"
- Show "Connecting..." while waiting for offer from child
- Show "Reconnecting..." if stream drops — auto-reconnect
- Controls: pause/resume, screenshot (save to gallery), fullscreen toggle
- Adaptive quality display: show current quality label (HD / SD / Low)

### Location Tab

- Full-screen OSMDroid map with live position
- Toggle: show 24-hour movement history as a polyline trail
- Geofence section:
  - Parent can draw or set radius for named zones (Home, School, Tuition)
  - Zones stored in Supabase and in DataStore locally
  - Alert shown in parent app if child exits a zone
- Location history list: timestamp + address for each logged position

### Monitoring Tab

- App Usage section:
  - List of apps used today with time spent per app
  - Pulled from app_usage table, grouped by app_name
  - Bar chart showing usage — most used app at top
- Call Logs section:
  - Incoming and outgoing calls with contact name and duration
- SMS Preview section:
  - Recent SMS messages with sender and preview text
- Contacts section:
  - Child's contacts list with name and phone number

### Controls Tab

All controls send a row to remote_commands table.
Every button shows a confirmation dialog before sending.
Every button shows sent/confirmed/failed state after sending.

- Lock Phone button → command = LOCK
- Trigger Alarm button → command = ALARM
- Block App button → opens app picker → command = BLOCK_APP
  payload = { "package": "com.example.app" }
- Send Message button → opens text input → command = MESSAGE
  payload = { "text": "Come home now" }
- Screen Time section:
  - Set daily limit per app category
  - Stored in Supabase, enforced by child app
- Safe Contacts section:
  - Parent defines a whitelist of allowed contacts
  - Child app restricts calls/SMS to only these numbers

### SOS Alert (Full-Screen Activity)

- Triggered automatically by sos_events Realtime subscription
- Also reachable from notification tap
- Full-screen red UI — impossible to miss
- Child's exact location on OSMDroid map (large, centered)
- One-tap call button → dials child's phone number immediately
- "I'm on my way" button → marks sos_event active = false in Supabase
- Timestamp of when SOS was triggered
- History section: list of all past SOS events with timestamps
- Must use:
  window.addFlags(
  WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
  WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
  WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
  )

---

## Device Linking Flow

1. Parent opens app for first time → Login with phone OTP
2. Parent enters child's name and age
3. App generates a random 6-digit family code:
   → Insert row in families table: { family_code: "123456" }
   → Store family_id in DataStore
4. Show the 6-digit code on screen with a QR code option
5. Parent installs child app on kid's phone and enters this code
6. Child app links itself to the same family_id
7. Parent app starts all 3 Realtime subscriptions once linking is confirmed

---

## OSMDroid Map Rules

- Never use Google Maps — OSMDroid only
- Initialize OSMDroid in Application class:
  Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
  Configuration.getInstance().userAgentValue = packageName
- Always set a tile cache to work offline:
  mapView.setTileSource(TileSourceFactory.MAPNIK)
  mapView.setUseDataConnection(true)
- Child location marker: use a distinct colored pin (red or orange)
- Geofence zones: draw as semi-transparent filled circles on the map
- Always call mapView.onResume() in onResume and mapView.onPause() in onPause
- Never block the main thread when loading map tiles

---

## Alert Rules

Push notifications are replaced entirely by Supabase Realtime.
Never use FCM. Never use OneSignal.

Notification channels to create in Application class:

CHANNEL_ID: "sos_alert"

- Importance: IMPORTANCE_HIGH
- Sound: loud alarm tone
- Vibration: enabled
- Used only for SOS events

CHANNEL_ID: "location_update"

- Importance: IMPORTANCE_LOW
- No sound
- Used for connectivity status updates only

CHANNEL_ID: "command_status"

- Importance: IMPORTANCE_DEFAULT
- Used for command sent / command confirmed feedback

SOS notification behavior:

- Fires immediately when sos_events INSERT arrives via Realtime
- Tapping notification opens SOSAlertActivity
- Cannot be dismissed by swipe — use setOngoing(true)
- Plays alarm sound on loop until parent taps "I'm on my way"

---

## UI / UX Rules

- Language: English only
- Theme: dark background preferred — easier to read outdoors in sunlight
- Bottom navigation bar with 5 tabs:
  Dashboard | Live Screen | Location | Monitoring | Controls
- SOS alert always takes over the full screen — no exceptions
- Loading states: every data fetch must show a spinner or skeleton
- Empty states: every list must show a helpful message when empty
  ("No app usage recorded today", "No SOS events — all clear ✓")
- Error states: every Supabase call failure must show a retry button
- Never show raw UUIDs or technical IDs to the parent
- Timestamps: always show in IST (Indian Standard Time, UTC+5:30)
  and in human-readable format ("Today 3:42 PM", "Yesterday 9:15 AM")

---

## Performance Rules

- All Supabase queries run on Dispatchers.IO — never on Main
- Map updates run on Main thread only — never block it
- Realtime subscriptions use callbackFlow — always cancel in onCleared()
- Image/icon loading: use Coil — never Glide or Picasso
- RecyclerView for all lists — never ScrollView with nested views
- Pagination: load 50 rows at a time for location history and app usage

---

## What Must Never Happen in This App

- Never poll Supabase on a timer — use Realtime subscriptions only
- Never use FCM, OneSignal, or any push notification service
- Never use Google Maps or Mapbox — OSMDroid only
- Never expose child's raw location data in notifications
- Never use supabaseClient.gotrue — always supabaseClient.auth (v3 API)
- Never use SharedPreferences — DataStore only
- Never use Firebase anything
- Never use Java — Kotlin only
- Never use !! operator — use ?: return or let patterns
- Never let the SOS alert be dismissed without parent acknowledgement
