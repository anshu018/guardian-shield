# Guardian Shield — Root Agent Rules

# This file is read by every agent working on this project.

# These rules are non-negotiable and override any agent defaults.

---

## Project Identity

- App name: Guardian Shield
- Purpose: Child safety monitoring for Indian parents with children under 10
- Submission: Smart India Hackathon (SIH)
- Two separate apps:
  - Child app package: com.guardianshield.child
  - Parent app package: com.guardianshield.parent
- Min SDK: 26 | Target SDK: 34
- Language: Kotlin only — never write a single line of Java

---

## The Stack (Final — Do Not Suggest Alternatives)

- Backend: Supabase (Postgres + Realtime + Auth + Storage)
- Maps: OSMDroid — never Google Maps, never Mapbox
- Auth: Supabase OTP phone auth — never email, never Google Sign-In
- Streaming: WebRTC P2P via MediaProjection
- Signaling: Railway.app Node.js + socket.io
- Push/Alerts: Supabase Realtime — never FCM, never OneSignal
- SMS fallback: Android SmsManager — for zero-internet SOS only
- DI: Hilt — never Koin, never manual Dagger
- Local storage: DataStore — never SharedPreferences, never SQLite directly
- Async: Kotlin Coroutines + Flow — never RxJava, never callbacks

---

## Architecture Rules

- Pattern: MVVM + Clean Architecture — always, no exceptions
- Layer structure every module must follow:
  - data/ → Supabase client, DataStore, DTOs, repository implementations
  - domain/ → Models, UseCase classes, repository interfaces
  - ui/ → Fragments, Activities, ViewModels, Composables if any
  - di/ → Hilt modules only
- ViewModels must never hold a Context reference
- ViewModels must never do I/O directly — always delegate to UseCases
- Repositories are the single source of truth
- UseCases: one public function, one responsibility, nothing else
- Never let the UI layer import anything from the data layer directly

---

## Kotlin Rules

- Always use `suspend fun` for one-shot async operations
- Always use `Flow` for streams (location, realtime events)
- Always use `StateFlow` or `SharedFlow` in ViewModels — never LiveData
- Always use `viewModelScope` for coroutines in ViewModels
- Always use `lifecycleScope` in Activities/Fragments
- Null safety: never use `!!` — use `?: return`, `?: continue`, or `let`
- Name coroutine scopes clearly in services: `private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`
- Cancel serviceScope in onDestroy() of every service

---

## Service Survival Rules (Critical for Indian OEM ROMs)

- Every foreground service must:
  - Return START_STICKY from onStartCommand()
  - Implement onTaskRemoved() that posts a delayed restart via AlarmManager
  - Show a persistent foreground notification — never run without one
  - Handle onDestroy() by restarting itself via Intent + AlarmManager
- BootReceiver must listen for both:
  - android.intent.action.BOOT_COMPLETED
  - android.intent.action.QUICKBOOT_POWERON ← required for Xiaomi, Realme
- ServiceWatchdogWorker via WorkManager must run every 15 minutes
- Never assume a service is running — always check before using

---

## Supabase Rules

- Initialize Supabase client as a singleton via Hilt — never instantiate it twice
- Always use the Supabase Kotlin client (io.github.jan-tennert.supabase)
- Every Supabase call must be wrapped in try/catch with specific error logging
- Realtime subscriptions must be set up in a coroutine with proper cancellation
- Never store Supabase URL or anon key in code — always read from BuildConfig
- Offline handling: cache last known state in DataStore, retry on reconnect

---

## Battery & Performance Rules (Critical for Indian ₹8,000 Phones)

- Location polling: 10s default → 5s during SOS → 30s when stationary
- Detect stationary: if distance < 20m over last 3 readings → switch to slow mode
- AppMonitor poll: every 30 seconds only
- WebRTC frame target: under 500KB/s — use adaptive quality
- Child APK size target: under 8MB (enforce with ProGuard)
- Never use PRIORITY_HIGH_ACCURACY location when battery < 15% — switch to BALANCED

---

## Security Rules

- Child app must block uninstall via DeviceAdminReceiver + setUninstallBlocked(true)
- Child app must hide itself: set component enabled state to DISABLED after setup
- Child app must not appear in recent apps: use FLAG_EXCLUDE_FROM_RECENTS on all Activities
- Only a parent-set PIN can disable Device Admin
- Never log sensitive data (location, phone numbers) — use placeholder logs in debug builds

---

## What Must Never Change

- The stack listed above is final — never suggest Firebase, Google Maps, or paid APIs
- Never use SharedPreferences — DataStore only
- Never use Java — Kotlin only
- Never skip the persistent foreground notification on services
- Never use hardcoded Supabase credentials in source files
