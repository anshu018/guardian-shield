# PRODUCT REQUIREMENTS DOCUMENT (PRD)

## Project: Guardian Shield

- **Event**: Smart India Hackathon (SIH) 2025
- **Version**: 1.0 (Production-Ready Spec)
- **Date**: April 2026
- **Builder**: Anshu (JBIT Dehradun)
- **Status**: In Development

---

## 1. Executive Summary & Vision

### 1.1 Product Vision

**To give every Indian parent absolute peace of mind regarding their child's physical safety - for free, forever, on any Android smartphone, with or without active internet connectivity.**

Guardian Shield is a dual-application ecosystem designed specifically for the unique economic and structural realities of the Indian consumer market. By combining a stealthy, highly resilient child-side background service app (`com.guardianshield.child`) with an intuitive, real-time parent-side dashboard app (`com.guardianshield.parent`), the system bridges the safety gap for millions of families at zero infrastructure cost.

### 1.2 The Problem

India has over 450 million children under the age of 14. Every day, Indian parents send their children to school, tuition, and playgrounds - and spend hours in uncertainty. They have no reliable, affordable way to know their child is safe in real time.

The core fear arises when a parent calls their child's phone and receives no answer. They do not know if the child is in class, stuck in traffic, or in danger.

### 1.3 Why Existing Solutions Fail in India

| Dimension               | Existing Global Solutions                                  | Guardian Shield Approach                                        |
| :---------------------- | :--------------------------------------------------------- | :-------------------------------------------------------------- |
| **Monthly Cost**        | ₹500 - ₹2,000 subscription model                           | **₹0 Free-Forever** (100% Zero-Cost Infrastructure)             |
| **Connectivity**        | Assumes continuous high-speed 4G/5G                        | **Resilient offline fallback** (2G SMS-based SOS routing)       |
| **System Survival**     | Easily terminated by aggressive OEM task-killers           | **3-layer background watchdog system**                          |
| **Device Requirements** | High-end CPU, optimized memory footprints                  | Highly optimized to run on **₹8,000 low-end smartphones**       |
| **UX & Auth**           | Complex email-based registration                           | **UPI-style Phone OTP** matching local mental models            |
| **Maps Stack**          | High-cost Google Maps API load charges                     | **OSMDroid** (Cached offline tiles, zero platform bills)        |
| **Backend Cost**        | Server relays or Firebase limits (bankrupting free models) | **Supabase DB + Realtime** with **WebRTC P2P direct streaming** |

---

## 2. Product Vision & Boundaries

### 2.1 What Guardian Shield Is

- **A fully automated safety net**: Operates silently without requiring active child participation.
- **An offline-resilient coordinate and status broadcaster**: Direct cellular fallback using SMS when data connections are dead.
- **A zero-cost media streamer**: Uses direct Peer-to-Peer (P2P) live video/audio checks without incurring media server relay fees.
- **A deep system-level background daemon**: Engineered specifically to prevent unauthorized force-kills by child users or OS optimization algorithms.

### 2.2 What Guardian Shield Is Not

- **Not spyware/malware**: It is explicitly installed by legal parents on devices they legally own and control, strictly for child protection.
- **Not a monetization vector**: The app contains zero ads, zero premium tiers, and zero data-brokering pipelines.
- **Not a bloated screen-time supervisor**: The focus is strictly child safety and physical monitoring, rather than heavy social media blocking or activity limitation.

### 2.3 The Personal Motivation

This project was built because the builder's own parents faced the fear of not knowing where their child was. Guardian Shield is personal. It is built to solve a real problem for real Indian families - not just to win a hackathon, but because the problem deserves a real solution.

---

## 3. Target Users & Personas

### 3.1 Primary User: The Indian Parent

- **Demographics**: Age 28-50 years old; resides in Tier-1, Tier-2, or Tier-3 cities; has children aged 5-14.
- **Technology Profile**: High familiarity with mobile interfaces through daily use of UPI (PhonePe, GPay, Paytm) and WhatsApp. Low to moderate technical literacy for advanced OS settings (permissions, overlays).
- **Core Pain Point**: Constant anxiety during commute times, tuition hours, and outdoor play. Needs immediate, one-tap verification that their child has safely arrived at their destination.

### 3.2 Secondary User: The Child (Passive)

- **Demographics**: Age 5-14.
- **Technology Profile**: Uses low-end or handed-down family smartphones. Needs zero interaction with the monitoring application, except in high-risk moments where an intuitive, prominent SOS interface is vital.

### 3.3 Out of Scope

- Teenagers managing their own devices (who require active communication tools rather than parental tracking).
- Parents seeking heavy academic screen-time limiting features.
- iOS users (Android only for this version due to restricted system-level background access on iOS).

---

## 4. Core Technology Stack & Rationale

Every technology choice is driven by a single core principle: **to build a fully functional, production-grade child safety system with a total infrastructure cost of ₹0 per month, forever.**

### 4.1 Stack Configuration

- **Supabase (PostgreSQL + Realtime + Auth)**
  - _Role_: Backend database, real-time sync, and phone number auth.
  - _Why_: Firebase free tier limits reads/writes to 50K/day. With GPS coordinates updating every 10 seconds, a single child device would exceed this limit within a few days of active use. Supabase PostgreSQL free tier has a generous 500MB storage limit and no read/write API throttling, making it ideal for continuous streaming of coordinates. Supabase Realtime uses efficient WebSockets, rendering data updates to the parent in under 1 second.

- **OSMDroid (OpenStreetMap)**
  - _Role_: Geographic maps rendering on the parent dashboard.
  - _Why_: Google Maps API charges per map load. A parent verifying coordinates multiple times a day would quickly trigger Google's commercial billing threshold. OSMDroid is open-source, completely free, caches tile data locally on the parent's storage to minimize network requests, and integrates seamlessly with custom location markers.

- **WebRTC Peer-to-Peer (`stream-webrtc-android`)**
  - _Role_: Ambient screen, camera, and audio streaming.
  - _Why_: Relaying real-time video through media servers is incredibly expensive. By using direct WebRTC Peer-to-Peer connections, video and audio are streamed directly from the child's device to the parent's device. No data is stored on a server, reducing server bandwidth cost to zero and ensuring complete privacy.

- **Railway.app (Signaling Server)**
  - _Role_: WebRTC handshake coordinator.
  - _Why_: WebRTC requires a signaling server to exchange initial SDP offers, answers, and ICE candidate metadata (<1KB total per handshake). Once connected, the signal server steps out, and data flows directly between the two devices. Railway's free tier easily accommodates the signaling needs of thousands of sessions.

- **Android SmsManager**
  - _Role_: Zero-connectivity emergency fallback.
  - _Why_: When cellular data drops entirely (0% coverage), the child app intercepts the network failure and falls back to sending SMS packets containing coordinates and distress alerts to registered parent phone numbers over standard 2G voice bands.

- **Hilt (Dagger)**
  - _Role_: Dependency injection.
  - _Why_: Catching dependency errors at compile time prevents runtime crashes on low-end hardware. Koin was rejected due to its runtime-only resolution pattern, which can lead to unpredictable crashes in long-running services.

- **Kotlin Coroutines + Flow**
  - _Role_: Asynchronous operations.
  - _Why_: Structured concurrency prevents memory leaks in background services, while `StateFlow` replaces deprecated and less efficient `LiveData`.

- **Jetpack DataStore**
  - _Role_: Local persistent storage.
  - _Why_: SharedPreferences lacks thread safety and easily crashes under concurrent writes from multiple services. DataStore is coroutine-safe and crash-safe.

- **WorkManager**
  - _Role_: Background watchdog daemon.
  - _Why_: Standard background alarms are frequently killed by custom Android operating systems. WorkManager is built on top of `JobScheduler` and is highly resilient to battery-saving policies, serving as a bulletproof final layer of defense.

### 4.2 Language & Architecture Parameters

- **Language**: 100% Kotlin (strictly banning Java files to maintain type-safe async pipelines and coroutines).
- **Architecture**: Clean Architecture with MVVM, strictly separating project code into L0 to L17 modular layers (Data, Domain, and UI layers).
- **Annotation Processor**: KSP (Kotlin Symbol Processing) only. KAPT is deprecated and runs 2x slower.
- **Min SDK**: API 26 (Android 8.0). Covers 98%+ of active Android devices in India, and supports `ForegroundServiceType` parameters.
- **Target SDK**: API 35 (Android 15). Required for Google Play compatibility, enforcing modern background execution types.

---

## 5. System Architecture & Flows

### 5.1 The Four Communication Planes

#### Plane 1: Location & Telemetry (Continuous Broadcast)

1. The child device's `LocationTrackingService` queries `FusedLocationProviderClient` every 10 seconds.
2. Coordinates (latitude, longitude, accuracy, speed), battery level, and timestamp are packaged and uploaded to the Supabase `child_location` table.
3. Supabase Realtime pushes the new row over a WebSocket connection to the parent app in <1 second.
4. The parent app parses the payload and dynamically repositions the OSMDroid map marker, leaving a faded trail showing the last 10 updates.

#### Plane 2: Ambient Stream (On-Demand WebRTC P2P)

1. The parent opens the "Live Screen" tab on their dashboard, triggering a "Stream Request" signal containing their `family_code` to the Railway Node.js server.
2. The Railway server broadcasts this request to the child's active socket connection.
3. The child app starts capturing screen frames via AccessibilityService.takeScreenshot().
4. Child and parent exchange SDP offer/answer packets and ICE candidates through Railway, establishing a direct WebRTC peer connection.
5. High-fidelity audio/video flows directly between child and parent devices. When the parent leaves the tab, the stream terminates, freeing up device resources.

#### Plane 3: Remote Commands (Event-Driven Execution)

1. The parent executes an action (e.g., locking the phone, triggering an alarm) on their dashboard app.
2. The parent app inserts a record into the `remote_commands` table (e.g., `{ command: "LOCK", executed: false }`).
3. The child app, which maintains an active Supabase Realtime WebSocket subscription on `remote_commands`, immediately receives the payload.
4. The child app executes the system action via `DevicePolicyManager` or `AudioManager`.
5. Upon successful execution, the child app patches the row (`{ executed: true, executed_at: now() }`), confirming the command back to the parent in real time.

#### Plane 4: Offline SOS Fallback (SMS Tunneling)

1. The child triggers an SOS event (either manually via a widget or automatically due to prolonged anomalies).
2. The system checks local network states. If no cellular data is active, `SmsManager` is invoked.
3. The system generates an encrypted SMS packet containing precise GPS coordinates formatted as a direct Google Maps link.
4. The SMS is sent directly to the parents' registered phone numbers. The parent app detects this incoming SMS and presents a full-screen emergency alert UI.

---

### 5.2 Database Schema & Row-Level Security (RLS)

To comply with personal data regulations (such as India's DPDP Act 2023), database design must guarantee that no parent can read or write any other family's telemetry. Row-Level Security is strictly enforced on all tables.

```sql
-- Enable Row-Level Security on all tables
ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE parents ENABLE ROW LEVEL SECURITY;
ALTER TABLE children ENABLE ROW LEVEL SECURITY;
ALTER TABLE child_location ENABLE ROW LEVEL SECURITY;
ALTER TABLE sos_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE remote_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_usage ENABLE ROW LEVEL SECURITY;

-- Families RLS Policies
CREATE POLICY "Families are readable only by verified members"
ON families FOR SELECT
USING (
  id IN (SELECT family_id FROM parents WHERE user_id = auth.uid())
);

-- Child Location RLS Policies
CREATE POLICY "Child locations are readable only by family parents"
ON child_location FOR SELECT
USING (
  child_id IN (
    SELECT c.id FROM children c
    JOIN parents p ON c.family_id = p.family_id
    WHERE p.user_id = auth.uid()
  )
);

CREATE POLICY "Child app can insert its own locations"
ON child_location FOR INSERT
WITH CHECK (true);
```

#### Table Definitions

##### `families`

- `id`: UUID (Primary Key)
- `family_code`: VARCHAR(6) (Unique index, 6-digit linking pin)
- `created_at`: TIMESTAMP WITH TIME ZONE

##### `parents`

- `id`: UUID (Primary Key)
- `user_id`: UUID (Foreign Key linking to `auth.users`)
- `family_id`: UUID (Foreign Key linking to `families.id`)
- `name`: VARCHAR(100)
- `phone`: VARCHAR(15) (Unique verification number)

##### `children`

- `id`: UUID (Primary Key)
- `family_id`: UUID (Foreign Key linking to `families.id`)
- `name`: VARCHAR(100)
- `age`: INTEGER
- `phone`: VARCHAR(15) (Optional)

##### `child_location`

- `id`: BIGSERIAL (Primary Key)
- `child_id`: UUID (Foreign Key linking to `children.id`)
- `latitude`: DOUBLE PRECISION
- `longitude`: DOUBLE PRECISION
- `battery_percentage`: INTEGER
- `accuracy_radius`: REAL
- `created_at`: TIMESTAMP WITH TIME ZONE

##### `sos_events`

- `id`: UUID (Primary Key)
- `child_id`: UUID (Foreign Key linking to `children.id`)
- `latitude`: DOUBLE PRECISION
- `longitude`: DOUBLE PRECISION
- `is_active`: BOOLEAN (Default: true)
- `triggered_at`: TIMESTAMP WITH TIME ZONE

##### `remote_commands`

- `id`: UUID (Primary Key)
- `child_id`: UUID (Foreign Key linking to `children.id`)
- `command_type`: VARCHAR(50) (e.g., LOCK, ALARM, BLOCK_APP)
- `payload`: JSONB
- `is_executed`: BOOLEAN (Default: false)
- `created_at`: TIMESTAMP WITH TIME ZONE
- `executed_at`: TIMESTAMP WITH TIME ZONE

##### `app_usage`

- `id`: BIGSERIAL (Primary Key)
- `child_id`: UUID (Foreign Key linking to `children.id`)
- `package_name`: VARCHAR(255)
- `app_name`: VARCHAR(100)
- `opened_at`: TIMESTAMP WITH TIME ZONE
- `closed_at`: TIMESTAMP WITH TIME ZONE

---

### 5.3 Background Service Survival Architecture

AGGRESSIVE task termination on custom Android operating systems (such as Xiaomi MIUI/HyperOS, Realme UI, and Samsung OneUI India) is the single biggest technical challenge. Guardian Shield uses a three-layer service survival architecture to ensure continuous tracking:

```
[System Event / Task Kill]
           |
           v
+-------------------------------------------------------------+
| Layer 1: Native Survival                                    |
| - Foreground Service Type: LOCATION                         |
| - Return START_STICKY from onStartCommand()                 |
| - onTaskRemoved() / onDestroy() schedule AlarmManager       |
+-------------------------------------------------------------+
           |
           | (If completely killed by OS task cleaner)
           v
+-------------------------------------------------------------+
| Layer 2: Boot & Wakeup Receivers                            |
| - Listen for android.intent.action.BOOT_COMPLETED           |
| - Listen for android.intent.action.QUICKBOOT_POWERON        |
| - Immediate relaunch of tracking engine                     |
+-------------------------------------------------------------+
           |
           | (If background execution drops entirely)
           v
+-------------------------------------------------------------+
| Layer 3: WorkManager Watchdog Daemon                        |
| - ServiceWatchdogWorker executes every 15 minutes           |
| - Programmatically inspects running services                |
| - Relaunches core LocationTrackingService if dead           |
+-------------------------------------------------------------+
```

#### Layer 1: Native Service Sticky State

- Foreground Service must declare `foregroundServiceType="location"` in the Android Manifest.
- The `onStartCommand()` function must return `START_STICKY`, instructing the OS to recreate the service immediately when memory becomes available.
- `onTaskRemoved()` and `onDestroy()` are overridden. They issue a pending intent to `AlarmManager` to programmatically restart the service in exactly 5 seconds.

#### Layer 2: Deep Boot Survival

The app registers a `BroadcastReceiver` listening to key boot sequences:

- `android.intent.action.BOOT_COMPLETED`
- `android.intent.action.QUICKBOOT_POWERON` (Required for Xiaomi, Realme, and older MTK chipsets common in budget Indian phones)
  On receiving these signals, the receiver restarts all background listeners immediately.

#### Layer 3: WorkManager Watchdog Daemon

- A periodic `ServiceWatchdogWorker` is registered with a 15-minute interval.
- The watchdog is scheduled using `ExistingPeriodicWorkPolicy.KEEP` to ensure it cannot be duplicated.
- When executed, it programmatically checks if `LocationTrackingService` is currently running. If it is dead, it launches it using `context.startForegroundService()`.
- **Why**: WorkManager is built on top of JobScheduler and is highly resilient to OEM battery saving policies, serving as a bulletproof final layer of defense.

---

## 6. Child App - Feature Specifications

- **Package Name**: `com.guardianshield.child`
- **App Manifest Name**: `System Services` (disguised as an OS package)
- **Target Size**: Under 8MB APK

### 6.1 Stealth & Protection System

To ensure safety in hostile environments (e.g. if the child is abducted or the phone is stolen), the child app must run completely silently and prevent unauthorized removal.

#### App Label & Icon

Disguised as a native system component named `System Services` using the default Android green robot or gear system icon.

#### Launcher Hide

Upon successful linking and permission setup, the launcher icon is completely hidden from the user's home screen and app drawer:

```kotlin
val p = packageManager
val componentName = ComponentName(context, MainActivity::class.java)
p.setComponentEnabledSetting(
    componentName,
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    PackageManager.DONT_KILL_APP
)
```

#### Recents Exclusions

The child app overrides all activities in the manifest to ensure they do not appear in the "Recent Apps" list:

```xml
android:excludeFromRecents="true"
```

#### Silent Persistent Notification

To run as a foreground service, Android requires a persistent notification. The child app configures a non-dismissible, low-priority notification:

- **Importance**: `IMPORTANCE_MIN` (No sound, no popup, no badge)
- **Title**: `System Core Process`
- **Body**: `Active system thread executing normally.`

#### Parents Re-entry Dial Code

Since the app icon is hidden, the parent can re-enter the configuration panel by opening the child's native phone dialer and dialing `*#1234#`. The app registers an outgoing call receiver:

- If the dialed string matches `*#1234#`, it aborts the outgoing broadcast and immediately launches the hidden `MainActivity`.

---

### 6.2 LocationTrackingService Spec

Runs continuously in the background, utilizing `FusedLocationProviderClient` with three adaptive modes to save battery:

#### 1. Normal Tracking Mode

- **Trigger**: Standard active monitoring.
- **Interval**: 10 seconds.
- **Accuracy**: `Priority.PRIORITY_HIGH_ACCURACY`.

#### 2. SOS Tracking Mode

- **Trigger**: Activated automatically when an SOS event is open for the family.
- **Interval**: 5 seconds.
- **Accuracy**: `Priority.PRIORITY_HIGH_ACCURACY`.

#### 3. Stationary Battery Saver Mode

- **Trigger**: If the last 3 consecutive GPS readings are all within a 20-meter radius (meaning the child is stationary in class, home, or tuition).
- **Interval**: 30 seconds.
- **Accuracy**: `Priority.PRIORITY_BALANCED_POWER_ACCURACY`.
- **Impact**: Decreases battery drain on low-end devices by over 70% while stationary.

#### 4. Battery Saver Fallback

- **Trigger**: Phone battery drops below 15%.
- **Interval**: 45 seconds.
- **Accuracy**: Forces location request priority to downgrade to `BALANCED` immediately.

#### 5. Offline Location Cache

- **Trigger**: Data connectivity drops to 0%.
- **Action**: Cache the last 5 coordinates locally in Jetpack DataStore. Upon reconnection, upload all cached coordinates with historical timestamps.

---

### 6.3 AppMonitorService Spec

- **Polling Frequency**: Every 30 seconds using `UsageStatsManager` to read the top active package in the foreground.
- **Action**: Inserts foreground activity log to Supabase `app_usage` table.
- **App Blocking**: Receives lists of blocked packages from the parent. If the child opens a blocked package, the service immediately launches a full-screen, overlay activity containing a message: `"Access Restricted by Parent"`, blocking further interaction.

---

### 6.4 SCREEN CAPTURE — AccessibilityService.takeScreenshot()

Guardian Shield uses AccessibilityService.takeScreenshot() (Android API 30+) instead
of MediaProjection for screen capture. This decision was made because MediaProjection
tokens become permanently invalid on phone restart, which destroys stealth by forcing
a visible permission dialog every time the phone reboots.

AccessibilityService.takeScreenshot() uses the Accessibility Service permission granted
during setup Step 2, which is permanent and requires no re-grant after any restart.
The capture is completely silent — no status bar indicator, no notification, no toast.
This is the same approach used by every professional parental monitoring app.

Adaptive frame rate:
- 5G/WiFi: 15fps, JPEG quality 80
- 4G: 12fps, JPEG quality 65  
- 3G: 8fps, JPEG quality 50
- 2G: 5fps, JPEG quality 35

---

### 6.5 Device Admin & Policy System

During setup, the child app requests Device Administrator privileges:

- **Blocks Uninstall**: Invokes `setUninstallBlocked(true)` to prevent the app from being uninstalled via Settings or dragged to the trash.
- **Remote Locking**: Executes `devicePolicyManager.lockNow()` instantly when receiving a `LOCK` command from the parent.
- **Deactivation Security**: Deactivating the Device Administrator profile requires entering the parent-set 4-digit PIN. If entered incorrectly 3 times, the phone immediately locks.

---

## 7. Parent App - Feature Specifications

- **Package Name**: `com.guardianshield.parent`
- **App Label**: `Guardian Shield`
- **Layout**: Modern 5-tab bottom navigation bar

### 7.1 Dashboard Tab

- **Live OSMDroid Map**: Renders child's live position using custom child avatar markers. Maps update dynamically over WebSockets without page refreshes. Renders child's 10-point movement history as a faded trail.
- **Child Status Panel**: Shows real-time child telemetry: battery level (with color-coded indicators), current open application, online/offline pill, and last-seen timestamp.
- **Status Warnings**:
  - If no update has been received for over 30 seconds: Status changes to `"Last seen X minutes ago"`.
  - If no update has been received for over 5 minutes: Dashboard presents a high-priority warning banner.

---

### 7.2 Live Screen Tab

- **WebRTC Stream Receiver**: Integrates `SurfaceViewRenderer` to render the child screen capture stream in real time.
- **Session States**: Displays `"Connecting..."`, `"Reconnecting..."`, or the active live stream.
- **Controls Overlay**: One-tap actions to capture a screenshot (saved directly to parent gallery) and toggle fullscreen mode.
- **Quality Badge**: Renders real-time quality indicators (HD, SD, Low, Very Low) based on current network bandwidth.

---

### 7.3 Location & Geofencing Tab

- **Geofence Editor**: Allows parents to draw radius-based circular geofences (Home, School, Tuition) directly on the OSMDroid map view.
- **Geofence Alerts**: The app monitors coordinates against active geofences. If child exits a zone, it issues a push notification.
- **Location History List**: Shows reverse-geocoded physical address listings with exact timestamps.

---

### 7.4 Monitoring & Telemetry Tab

- **App Usage Analytics**: Renders a beautiful horizontal bar chart showing apps used today sorted by time spent.
- **Telemetry Feeds**: Lists call logs, SMS previews, and the child's contacts list retrieved securely from Supabase.

---

### 7.5 Controls Tab

A high-priority control center allowing parents to issue direct commands:

- **Lock Device**: Forces child phone to lock instantly.
- **Trigger Siren**: Commands child phone to loop a loud alarm sound at maximum volume, overriding silent/Do Not Disturb modes.
- **Block Application**: Add/remove package names to the restricted list.
- **Send Warning Message**: Renders a full-screen persistent dialog with the parent's custom text over the child's interface.

---

### 7.6 Full-Screen SOS Emergency Activity

When a child triggers an SOS event, the parent app receives the row insertion over Supabase Realtime and triggers a critical full-screen activity:

```
+-----------------------------------------------------------+
| ! EMERGENCY ALERT !                       [Battery: 84%]  |
| Aaradhya has triggered distress signal!                   |
+-----------------------------------------------------------+
|                                                           |
|                     OSMDroid Map View                     |
|                                                           |
|                     [ Aaradhya Marker ]                   |
|                                                           |
+-----------------------------------------------------------+
| Address: 12, Rajpur Road, Dehradun                        |
+-----------------------------------------------------------+
|   [ CALL CHILD ]      |       [ I'M ON MY WAY ]           |
|  Direct phone dial    |   Acknowledge, stop parent alarm  |
+-----------------------------------------------------------+
```

- **Lock-Screen Override**: Appears even if the parent phone is locked:
  ```kotlin
  window.addFlags(
      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
      WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
  )
  ```
- **Siren Loop**: Plays a continuous, high-volume alarm sound that loops until the parent acknowledges the alert.
- **One-Tap Dialing**: A prominent button directly dials the child's phone number.
- **Non-Dismissible**: Ongoing notification keeps the screen active; cannot be closed by standard swipes.

---

## 8. Device Onboarding & Linking Flow

To ensure high compliance for non-technical Indian parents, the linking mechanism utilizes a 6-digit code matching the mental model of banking OTPs and UPI.

### Android 13+ Restricted Settings Bypass

Google blocks sideloaded apps from enabling Accessibility Services and Device Admin by default ("Restricted Settings"). The setup wizard must guide parents to bypass this block step by step:

```
[Parent dials *#1234# to enter Child app setup]
                       |
                       v
+-----------------------------------------------------------+
| Step 1: Open Phone Settings                               |
| -> Go to Apps -> System Services                          |
+-----------------------------------------------------------+
                       |
                       v
+-----------------------------------------------------------+
| Step 2: Click Three Dots (Top-Right)                      |
| -> Tap "Allow Restricted Settings"                        |
+-----------------------------------------------------------+
                       |
                       v
+-----------------------------------------------------------+
| Step 3: Enter Child App Setup Wizard Again                |
| -> Accessibility & Device Admin can now be safely enabled! |
+-----------------------------------------------------------+
```

#### Step-by-Step Linking Flow:

1. **Parent App**: Parent registers via phone OTP, inputs child's name and age, and taps "Generate Code."
2. **Backend**: Supabase generates a random, unique `family_code` (valid for 15 minutes) and inserts it into the `families` table.
3. **Child App Installation**: Parent sideloads the child APK on the kid's phone.
4. **Wizard Setup**: Parent dials `*#1234#` to launch the Child setup panel. The wizard guides the parent to grant Fine Location, Accessibility, Usage Stats, and Device Admin permissions (explicitly showing how to bypass Android 13+ Restricted Settings).
5. **Linking Verification**: Parent inputs the 6-digit code or scans the QR code generated on the parent's app.
6. **Confirmation**: The child app matches the code against the database, updates the child's `family_id` in Jetpack DataStore, starts all background services, and hides the launcher icon. The parent dashboard refreshes automatically, showing the connected child's avatar.

---

## 9. Non-Functional Requirements & Compliance

### 9.1 Performance Indicators

- **Map Update Latency**: <2 seconds from location change to map marker movement (achieved via Supabase Realtime).
- **SOS Signal Ingestion**: <3 seconds from trigger to full-screen parent alert.
- **Remote Command Latency**: <5 seconds from parent control tap to child execution.
- **WebRTC Connection Establish Time**: <8 seconds for signaling handshake and ICE candidate exchange.
- **System Footprint**: Child APK size strictly under 8MB using aggressive ProGuard minification.
- **Battery Footprint**: GPS monitoring consumes less than 5% total daily battery through adaptive stationary polling algorithms.

### 9.2 Compliance: DPDP Act 2023 & GDPR

India's Digital Personal Data Protection Act (DPDP Act 2023) mandates explicit, verifiable parental consent for processing children's personal data.

1. **Verifiable Consent Flow**: During parent signup, the app forces a step validating that the parent holds the primary phone plan, with a mandatory terms acceptance checkbox: `"I verify that I am the legal guardian of the child being monitored."`
2. **Explicit Data Purge**: Parents can tap `"Delete Family Account & Purge Data"` in settings. This triggers a Postgres cascade deletion, immediately erasing all call logs, SMS previews, location history, and account records.
3. **Local Encryption**: Jetpack DataStore caches are encrypted using the Android Keystore system.
4. **Transport Security**: All API endpoints use TLS 1.3.

---

## 10. 18-Layer Implementation & Build Plan

Both apps must compile successfully after each layer. We enforce a zero-leak policy: errors must be fixed in the layer they originate.

- **L0: Documentation** - Complete all `.md` files (root AGENTS.md, skill files, app-specific specifications).
- **L1: Scaffold** - multi-module directory structure, manifest setup, Hilt dependencies, KSP configuration.
- **L2: DB Schema & Supabase Auth** - Database tables, RLS scripts, Supabase Phone OTP auth UI and repository layers.
- **L3: GPS Telemetry** - `LocationTrackingService` with FusedLocation provider and Jetpack DataStore offline cache.
- **L4: Background Service Survival** - BootReceivers, AlarmManager restart hooks, `ServiceWatchdogWorker` setup.
- **L5: Device Admin** - `DeviceAdminReceiver` integration, LOCK command configuration, and PIN deactivation panel.
- **L6: App Monitor Service** - `UsageStatsManager` listener, block app full-screen overlay dialogs.
- **L7: SOS Engine** - Stationary anomaly algorithm, manual SOS button trigger, and automated SMS sending.
- **L8: WebRTC Signaling** - Railway signaling Node.js server setup with ICE candidate exchange WebSockets.
- **L9: ScreenCaptureService (REVISED)** - `AccessibilityService.takeScreenshot()` screen capture and P2P streaming implementation.
- **L10: Live Map Integration** Renders realtime maps using OSMDroid.
- **L11: WebRTC Live Screen Viewer** Parent app WebRTC client renderer with connection state and quality indicators.
- **L12: Monitoring Metrics** Renders call logs, SMS feeds, and contact profiles.
- **L13: Remote Commands Panel** Integration of LOCK, ALARM, BLOCK_APP, and MESSAGE commands.
- **L14: Emergency SOS Alert Activity** Lockscreen-overriding emergency sirens and single-tap dialing.
- **L15: Setup Wizard** Step-by-step parent wizard explaining Restricted Settings bypass.
- **L16: Device Linking** 6-digit family linking codes and QR codes.
- **L17: Validation Testing** Live field testing for coordinates accuracy and device battery impact.

---

## 11. Final Decisions (Locked)

These decisions are locked and represent final technical constraints for all contributors:

- **Backend**: Supabase only (Firebase rejected due to free tier read/write limitations).
- **Maps**: OSMDroid only (Google Maps rejected due to usage billing costs).
- **Push Notifications**: Supabase Realtime (FCM rejected because cheap Indian devices often lack Google services).
- **Authentication**: Phone OTP only (Indian users prefer numbers over complex email registrations).
- **Async**: Kotlin Coroutines + Flow (LiveData rejected as deprecated).
- **DI**: Hilt only (Koin rejected due to runtime dependencies failure risks).
- **Storage**: Jetpack DataStore only (SharedPreferences rejected due to concurrent write crash risks).
- **Language**: Kotlin only (Java files strictly banned).
- **Annotation**: KSP only (KAPT rejected as deprecated and slow).
- **Streaming**: WebRTC Peer-to-Peer only (paid server relays are completely out of scope).

---

## 12. Risk Register & Mitigations

### R-01: Aggressive Background Service Termination

- **Severity**: High
- **Mitigation**: 3-layer watchdog (START_STICKY + AlarmManager + WorkManager). If killed, the app restarts in under 5 seconds, backed by a WorkManager daemon running every 15 minutes.

### R-02: WebRTC Connection Fails Behind Symmetric NATs

- **Severity**: Medium
- **Mitigation**: The parent and child apps integrate public STUN servers. If NAT traversal fails, the app automatically falls back to Supabase Realtime command updates, ensuring alerts are still received.

### R-03: Restricted Settings Blocks Accessibility on Android 13+

- **Severity**: High
- **Mitigation**: The onboarding Setup Wizard features a clear, visual step-by-step guide showing parents exactly how to go to settings, click the three dots, and enable Restricted Settings.

---

## 13. Critical Developer Instructions (For AI & Human Contributors)

1. **Strict Kotlin Compliance**: Do not write a single line of Java code. All async pipelines must use Kotlin Coroutines and Flows.
2. **Never Hardcode Secrets**: Supabase URL and keys must reside strictly in `local.properties` (loaded via `BuildConfig`). Never commit secrets to Git.
3. **No LiveData**: All ViewModels must expose states via `StateFlow` and trigger side-effects using `SharedFlow`.
4. **Defensive Programming**: Never use the `!!` operator. Always enforce null safety using `?: return`, `?: continue`, or `let`.
5. **Continuous Compilation**: Both `:child-app` and `:parent-app` must compile successfully (`assembleDebug`) after every change. Never check in broken code.
