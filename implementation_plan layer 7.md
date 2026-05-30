# Implementation Plan - Layer 7: SOS Engine

Implement **Layer 7: SOS Engine** in the child-app module (`com.guardianshield.child`) of the **Guardian Shield** child safety ecosystem. This layer is critical for child physical safety, ensuring robust monitoring, zero-internet offline emergency communication fallback, and low-latency command execution.

## User Review Required

> [!IMPORTANT]
> **Dynamic Network & Telemetry Adjustments**:
> - The location service will automatically dynamically transition to `ACCESS_COARSE_LOCATION` (`NETWORK_PROVIDER` updates only) under low battery (< 15%) when SOS is not active. This achieves `PRIORITY_BALANCED_POWER_ACCURACY` as per high-level constraints.
> - When SOS is active, both `GPS_PROVIDER` and `NETWORK_PROVIDER` are immediately activated with a high-frequency **5-second polling interval** regardless of battery level to prioritize emergency safety.

> [!WARNING]
> **Offline SMS Encryption Key**:
> - A static key derived from `GS_SECRET_KEY_12` (128-bit AES/CBC/PKCS5Padding) is used to encrypt SMS coordinate packets. In later device-linking stages (Layer 16), we can dynamically derive keys from the shared family pin.

## Open Questions

> [!NOTE]
> There are no major blockages. We have defined robust dummy/default values (e.g. registered parent phone `+919876543210`, child name `Child`) in `LocationDataStore` so that the app compiles and is fully testable prior to the integration of the Pairing Code & Setup Wizard (Layer 15/16).

---

## Proposed Changes

### 1. Data Layer

#### [MODIFY] [LocationDataStore.kt](file:///c:/Users/ash74/OneDrive/Desktop/SIH-%201/guardian-shield/child-app/src/main/kotlin/com/guardianshield/child/data/local/LocationDataStore.kt)
- Add Jetpack DataStore preferences keys:
  - `PARENT_PHONE`: Parent's phone number for emergency SMS routing.
  - `CHILD_NAME`: Child's name to include in SMS messages.
  - `STATIONARY_START_TIME`: Timestamp when child entered stationary mode to compute the 30-minute auto-SOS threshold across restarts.
- Add helper methods:
  - `saveParentPhone(phone: String)` / `getParentPhone(): String?` (returns `"+919876543210"` as default).
  - `saveChildName(name: String)` / `getChildName(): String?` (returns `"Child"` as default).
  - `saveStationaryStartTime(time: Long)` / `getStationaryStartTime(): Long`.

### 2. Utilities

#### [NEW] [CryptoUtils.kt](file:///c:/Users/ash74/OneDrive/Desktop/SIH-%201/guardian-shield/child-app/src/main/kotlin/com/guardianshield/child/utils/CryptoUtils.kt)
- Create a secure utility using Kotlin's standard library and `javax.crypto`.
- Support AES/CBC/PKCS5Padding encryption and decryption.
- Base64 encode encrypted packets to form standard-printable SMS characters.

### 3. Services (Telemetry & Enforcement)

#### [MODIFY] [LocationTrackingService.kt](file:///c:/Users/ash74/OneDrive/Desktop/SIH-%201/guardian-shield/child-app/src/main/kotlin/com/guardianshield/child/services/LocationTrackingService.kt)
- Update `detectStationaryState`:
  - When stationary state is detected, save the current timestamp as `STATIONARY_START_TIME` in `LocationDataStore` (if not already set).
  - When movement resumes, clear `STATIONARY_START_TIME` (set to `0L`).
- Introduce a cohesive `updateTrackingParameters()` method that integrates multiple constraints:
  - `isSosActive`: Poll every 5s (`GPS` + `NETWORK` high accuracy).
  - `batteryLevel <= 15%`: Poll every 45s (`NETWORK` balanced accuracy).
  - `isStationaryMode`: Poll every 30s (`GPS` + `NETWORK`).
  - `Normal`: Poll every 10s (`GPS` + `NETWORK`).
- Accept `EXTRA_SOS_ACTIVE` boolean extra in `onStartCommand` to toggle high-frequency emergency polling dynamically.

#### [MODIFY] [EmergencySOSService.kt](file:///c:/Users/ash74/OneDrive/Desktop/SIH-%201/guardian-shield/child-app/src/main/kotlin/com/guardianshield/child/services/EmergencySOSService.kt)
- Implement **Stationary States Monitor**:
  - Run a coroutine-based checker every 60 seconds reading `STATIONARY_START_TIME`.
  - Auto-trigger SOS if duration > 30 minutes.
- Support **Manual SOS Trigger**:
  - Intercept `ACTION_TRIGGER_MANUAL_SOS` in `onStartCommand` (ready for home screen widget hooks).
- Implement **SOS Trigger Logic**:
  - Query active network state.
  - If active internet:
    - Insert a record into Supabase `sos_events` table.
    - Notify `LocationTrackingService` to switch to 5-second updates via intent extra.
  - If internet is inactive (0%):
    - Retrieve registered parent phone and child name.
    - Build a Google Maps URL containing exact location.
    - Encrypt payload using `CryptoUtils` and format message packet.
    - Dispatch text message using `SmsManager`.
    - Notify `LocationTrackingService` to switch to 5-second updates (to cache locations locally).
- Implement **Remote Commands Listener**:
  - Establish a Supabase Realtime channel subscription to the `remote_commands` table filtered by `child_id`.
  - On `LOCK` command: Call `GuardianDeviceAdminReceiver.lockDevice(applicationContext)`.
  - On `ALARM` command: Override sound settings using `AudioManager` and loop a system default alarm sound at max `STREAM_ALARM` volume. If command contains `"stop"`, terminate loop.
  - On `MESSAGE` command: Programmatically inject a gorgeous dark-mode full-screen system alert overlay with the parent's message using `WindowManager`. Include a user-friendly acknowledgement button.
  - Update `remote_commands` row status (`is_executed = true` and `executed_at = Instant.now()`) on successful execution.

---

## Verification Plan

### Automated Build Verification
- Compile child-app:
  ```powershell
  ./gradlew :child-app:assembleDebug
  ```
- Compile parent-app:
  ```powershell
  ./gradlew :parent-app:assembleDebug
  ```
- Check that build succeeds without any errors.

### Manual / Device Verification
- Trigger simulated BOOT broadcast to confirm receiver starts all services:
  ```powershell
  adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
  ```
- Monitor Logcat output for:
  - Stationary battery saver transition triggers.
  - Supabase database insertion or secure SMS fallback activation when data connectivity is toggled off.
  - Remote command reception (LOCK, ALARM, MESSAGE) from the parent dashboard database.
