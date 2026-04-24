# Supabase Android Skill

# Load this skill for any task involving Supabase: auth, database, or realtime.

---

## What This Skill Covers

Any agent working on L2, L3, L6, L7, L10, L12, or L13 must read
this entire file. This covers every Supabase operation used across
both child-app and parent-app.

---

## Dependency Setup

```kotlin
// In libs.versions.toml
[versions]
supabase = "2.1.0"
ktor = "2.3.7"

[libraries]
supabase-postgrest = { module = "io.github.jan-tennert.supabase:postgrest-kt", version.ref = "supabase" }
supabase-realtime = { module = "io.github.jan-tennert.supabase:realtime-kt", version.ref = "supabase" }
supabase-gotrue = { module = "io.github.jan-tennert.supabase:gotrue-kt", version.ref = "supabase" }
ktor-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }

// In build.gradle.kts (app level)
implementation(libs.supabase.postgrest)
implementation(libs.supabase.realtime)
implementation(libs.supabase.gotrue)
implementation(libs.ktor.android)
```

---

## Client Initialization

```kotlin
// Always a singleton via Hilt — never instantiate more than once
// Lives in di/AppModule.kt

@Provides
@Singleton
fun provideSupabaseClient(): SupabaseClient {
    return createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Realtime)
        install(GoTrue) {
            scheme = "guardianshield"   // for OTP deep link callback
            host = "auth"
        }
    }
}
```

BuildConfig setup in build.gradle.kts:

```kotlin
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${project.findProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY")}\"")
    }
}
```

Store actual values in local.properties — never in source code:
SUPABASE_URL=https://yourproject.supabase.co
SUPABASE_ANON_KEY=your-anon-key-here

---

## OTP Phone Auth Flow

```kotlin
// Step 1 — Send OTP (child-app and parent-app both use this)
suspend fun sendOtp(phone: String): Result<Unit> {
    return try {
        supabaseClient.gotrue.sendOtp(
            type = OtpType.Phone(phone) // phone format: +91XXXXXXXXXX
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Step 2 — Verify OTP
suspend fun verifyOtp(phone: String, token: String): Result<Unit> {
    return try {
        supabaseClient.gotrue.verifyPhoneOtp(
            phone = phone,
            token = token,
            type = OtpType.SMS
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Step 3 — Get current session
fun getCurrentUserId(): String? {
    return supabaseClient.gotrue.currentSessionOrNull()?.user?.id
}

// Step 4 — Check if logged in on app start
fun isLoggedIn(): Boolean {
    return supabaseClient.gotrue.currentSessionOrNull() != null
}
```

---

## Database Schema Reference

```sql
-- All tables for reference — do not recreate, already exists in Supabase

families        (id uuid, family_code text unique, created_at timestamptz)
parents         (id uuid, family_id uuid, name text, phone text, user_id uuid)
children        (id uuid, family_id uuid, name text, age int, phone text)
child_location  (id uuid, child_id uuid, lat float8, lng float8,
                 battery int, accuracy float4, timestamp timestamptz)
sos_events      (id uuid, child_id uuid, lat float8, lng float8,
                 active bool, triggered_at timestamptz)
app_usage       (id uuid, child_id uuid, package_name text, app_name text,
                 opened_at timestamptz, duration_seconds int)
remote_commands (id uuid, child_id uuid, command text, payload jsonb,
                 executed bool, created_at timestamptz)

-- Realtime enabled on: child_location, sos_events, remote_commands
```

---

## DTO Pattern

```kotlin
// DTOs live in data/remote/ — separate from domain models
// Use @Serializable for Supabase Kotlin client

@Serializable
data class ChildLocationDto(
    val id: String? = null,
    @SerialName("child_id") val childId: String,
    val lat: Double,
    val lng: Double,
    val battery: Int,
    val accuracy: Float,
    val timestamp: String? = null  // Supabase returns ISO string
)

// Always map DTOs to domain models — never expose DTOs to UI layer
fun ChildLocationDto.toDomain() = ChildLocation(
    childId = childId,
    lat = lat,
    lng = lng,
    battery = battery,
    accuracy = accuracy,
    timestamp = System.currentTimeMillis()
)

fun ChildLocation.toDto() = ChildLocationDto(
    childId = childId,
    lat = lat,
    lng = lng,
    battery = battery,
    accuracy = accuracy
)
```

---

## Insert Pattern

```kotlin
// Uploading child location every 10 seconds
suspend fun uploadLocation(dto: ChildLocationDto): Result<Unit> {
    return try {
        supabaseClient.postgrest
            .from("child_location")
            .insert(dto)
        Result.success(Unit)
    } catch (e: HttpRequestException) {
        // Network error — cache locally and retry
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Inserting SOS event
suspend fun triggerSos(dto: SosEventDto): Result<Unit> {
    return try {
        supabaseClient.postgrest
            .from("sos_events")
            .insert(dto)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

## Query Pattern

```kotlin
// Get latest location for a child
suspend fun getLatestLocation(childId: String): ChildLocationDto? {
    return try {
        supabaseClient.postgrest
            .from("child_location")
            .select {
                filter { eq("child_id", childId) }
                order("timestamp", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<ChildLocationDto>()
    } catch (e: Exception) {
        null
    }
}

// Get app usage for today
suspend fun getTodayAppUsage(childId: String): List<AppUsageDto> {
    return try {
        supabaseClient.postgrest
            .from("app_usage")
            .select {
                filter {
                    eq("child_id", childId)
                    gte("opened_at", todayStartIso()) // helper function
                }
                order("opened_at", Order.DESCENDING)
            }
            .decodeList<AppUsageDto>()
    } catch (e: Exception) {
        emptyList()
    }
}
```

---

## Realtime Subscription Pattern

```kotlin
// Parent app subscribes to child location updates
// Run this in a coroutine — cancel when ViewModel is cleared

fun observeChildLocation(childId: String): Flow<ChildLocationDto> = callbackFlow {
    val channel = supabaseClient.realtime.channel("child_location_$childId")

    channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
        table = "child_location"
        filter = "child_id=eq.$childId"
    }.collect { change ->
        trySend(change.decodeRecord<ChildLocationDto>())
    }

    channel.subscribe()

    // Clean up when flow is cancelled
    awaitClose {
        supabaseClient.realtime.removeChannel(channel)
    }
}

// Parent app subscribes to SOS events
fun observeSosEvents(childId: String): Flow<SosEventDto> = callbackFlow {
    val channel = supabaseClient.realtime.channel("sos_$childId")

    channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
        table = "sos_events"
        filter = "child_id=eq.$childId"
    }.collect { change ->
        trySend(change.decodeRecord<SosEventDto>())
    }

    channel.subscribe()
    awaitClose { supabaseClient.realtime.removeChannel(channel) }
}

// Child app listens for remote commands from parent
fun observeRemoteCommands(childId: String): Flow<RemoteCommandDto> = callbackFlow {
    val channel = supabaseClient.realtime.channel("commands_$childId")

    channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
        table = "remote_commands"
        filter = "child_id=eq.$childId"
    }.collect { change ->
        trySend(change.decodeRecord<RemoteCommandDto>())
    }

    channel.subscribe()
    awaitClose { supabaseClient.realtime.removeChannel(channel) }
}
```

---

## Offline Caching Strategy

```kotlin
// When Supabase upload fails, cache in DataStore and retry on reconnect
// Implement this in LocationRepositoryImpl

private val pendingUploads = mutableListOf<ChildLocationDto>()
// Max 5 cached locations as defined in project spec

suspend fun uploadWithFallback(location: ChildLocation) {
    val dto = location.toDto()
    val result = uploadLocation(dto)

    result.onFailure {
        // Cache locally — max 5 locations
        if (pendingUploads.size >= 5) pendingUploads.removeAt(0)
        pendingUploads.add(dto)
        locationDataStore.savePendingUpload(dto)
    }
}

// Call this when network becomes available
suspend fun retryPendingUploads() {
    val pending = locationDataStore.getPendingUploads()
    pending.forEach { dto ->
        val result = uploadLocation(dto)
        result.onSuccess {
            locationDataStore.removePendingUpload(dto)
        }
    }
}
```

---

## Family Code Flow

```kotlin
// Parent app — generate 6-digit family code
suspend fun generateFamilyCode(): Result<String> {
    return try {
        val code = (100000..999999).random().toString()
        supabaseClient.postgrest
            .from("families")
            .insert(mapOf("family_code" to code))
        Result.success(code)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Child app — link to family using 6-digit code
suspend fun linkToFamily(code: String, childName: String, age: Int): Result<Unit> {
    return try {
        val family = supabaseClient.postgrest
            .from("families")
            .select { filter { eq("family_code", code) } }
            .decodeSingleOrNull<FamilyDto>()
            ?: return Result.failure(Exception("Invalid family code"))

        supabaseClient.postgrest
            .from("children")
            .insert(
                ChildDto(
                    familyId = family.id,
                    name = childName,
                    age = age,
                    phone = getCurrentUserPhone()
                )
            )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

## What to Never Do

- Never initialize SupabaseClient more than once — singleton only
- Never store credentials in source code — always BuildConfig from local.properties
- Never expose DTOs to the UI layer — always map to domain models
- Never skip try/catch on any Supabase call
- Never create multiple Realtime channels for the same table+filter
- Never forget awaitClose in callbackFlow — it leaks the channel
- Never use decodeList when you expect a single result — use decodeSingleOrNull
