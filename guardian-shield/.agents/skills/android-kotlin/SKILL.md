# Android Kotlin Skill

# Load this skill for any task involving Kotlin code, ViewModels, repositories, or UI.

---

## What This Skill Covers

Any agent writing Kotlin code for either child-app or parent-app must
read this entire file first. This defines every pattern used across
all 18 layers of Guardian Shield.

---

## Project Architecture Overview

app/
├── data/
│ ├── remote/ → Supabase DTOs and API calls
│ ├── local/ → DataStore implementations
│ └── repository/ → Repository implementations
├── domain/
│ ├── models/ → Pure Kotlin data classes, no Android imports
│ ├── repository/ → Repository interfaces only
│ └── usecases/ → One class, one function, one job
├── ui/
│ ├── screen_name/
│ │ ├── ScreenFragment.kt
│ │ └── ScreenViewModel.kt
├── services/ → All foreground services (child app only)
├── admin/ → DeviceAdminReceiver (child app only)
├── di/ → All Hilt modules
└── utils/ → Extension functions, constants only

---

## ViewModel Pattern

```kotlin
// Every ViewModel follows this exact structure
@HiltViewModel
class LocationViewModel @Inject constructor(
    private val getChildLocationUseCase: GetChildLocationUseCase
) : ViewModel() {

    // Use StateFlow for UI state — never LiveData
    private val _locationState = MutableStateFlow<LocationState>(LocationState.Loading)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    init {
        observeLocation()
    }

    private fun observeLocation() {
        viewModelScope.launch {
            getChildLocationUseCase()
                .catch { e -> _locationState.value = LocationState.Error(e.message) }
                .collect { location -> _locationState.value = LocationState.Success(location) }
        }
    }
}

// Sealed class for every UI state — never use raw strings for state
sealed class LocationState {
    object Loading : LocationState()
    data class Success(val location: ChildLocation) : LocationState()
    data class Error(val message: String?) : LocationState()
}
```

---

## Repository Pattern

```kotlin
// Interface lives in domain/ — no Android imports allowed here
interface LocationRepository {
    fun observeChildLocation(childId: String): Flow<ChildLocation>
    suspend fun uploadLocation(location: ChildLocation): Result<Unit>
    suspend fun getCachedLocation(): ChildLocation?
}

// Implementation lives in data/ — Android imports allowed here
class LocationRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val locationDataStore: LocationDataStore
) : LocationRepository {

    override fun observeChildLocation(childId: String): Flow<ChildLocation> = flow {
        // Supabase realtime subscription here
    }

    override suspend fun uploadLocation(location: ChildLocation): Result<Unit> {
        return try {
            // Supabase insert here
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCachedLocation(): ChildLocation? {
        return locationDataStore.getLastKnownLocation()
    }
}
```

---

## UseCase Pattern

```kotlin
// One class, one public function, one responsibility
// Lives in domain/usecases/ — zero Android imports
class GetChildLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    // operator fun invoke() lets you call it like a function: useCase()
    operator fun invoke(childId: String): Flow<ChildLocation> {
        return locationRepository.observeChildLocation(childId)
    }
}

class UploadLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(location: ChildLocation): Result<Unit> {
        return locationRepository.uploadLocation(location)
    }
}
```

---

## Domain Model Pattern

```kotlin
// Pure Kotlin — zero Android imports, zero Supabase imports
// Lives in domain/models/
data class ChildLocation(
    val childId: String,
    val lat: Double,
    val lng: Double,
    val battery: Int,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class SosEvent(
    val id: String,
    val childId: String,
    val lat: Double,
    val lng: Double,
    val active: Boolean,
    val triggeredAt: Long
)

data class RemoteCommand(
    val id: String,
    val childId: String,
    val command: CommandType,
    val payload: Map<String, String> = emptyMap(),
    val executed: Boolean = false
)

enum class CommandType {
    LOCK, ALARM, BLOCK_APP, MESSAGE
}
```

---

## DataStore Pattern

```kotlin
// Never use SharedPreferences — always DataStore
// One DataStore per concern — don't dump everything in one file

class LocationDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore(name = "location_cache")

    companion object {
        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LNG = doublePreferencesKey("last_lng")
        val LAST_TIMESTAMP = longPreferencesKey("last_timestamp")
    }

    suspend fun saveLastKnownLocation(lat: Double, lng: Double) {
        context.dataStore.edit { prefs ->
            prefs[LAST_LAT] = lat
            prefs[LAST_LNG] = lng
            prefs[LAST_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun getLastKnownLocation(): ChildLocation? {
        val prefs = context.dataStore.data.first()
        val lat = prefs[LAST_LAT] ?: return null
        val lng = prefs[LAST_LNG] ?: return null
        return ChildLocation(childId = "", lat = lat, lng = lng, battery = 0, accuracy = 0f)
    }
}
```

---

## Hilt DI Module Pattern

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context
    ): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Realtime)
            install(GoTrue)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // Use @Binds to bind interface to implementation — cleaner than @Provides
    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl
    ): LocationRepository
}
```

---

## Coroutines & Flow Rules

```kotlin
// In ViewModels — always use viewModelScope
viewModelScope.launch { }
viewModelScope.launch(Dispatchers.IO) { } // for heavy I/O

// In Services — always use a named serviceScope
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
// Cancel in onDestroy():
override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
}

// Collecting Flow in Fragment — always use repeatOnLifecycle
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.locationState.collect { state ->
            when (state) {
                is LocationState.Loading -> showLoading()
                is LocationState.Success -> updateMap(state.location)
                is LocationState.Error -> showError(state.message)
            }
        }
    }
}

// Never use GlobalScope — it leaks and cannot be cancelled
// Never use lifecycleScope.launch outside of STARTED state check
```

---

## Null Safety Rules

```kotlin
// Never use !! — it crashes in production
// Use these patterns instead:

// Pattern 1: return early
val location = getLocation() ?: return

// Pattern 2: return early in suspend fun
val childId = session.childId ?: return Result.failure(Exception("No child ID"))

// Pattern 3: use let for nullable operations
location?.let { updateMap(it) }

// Pattern 4: provide defaults
val battery = intent.getIntExtra("battery", 0)
```

---

## Fragment & Activity Rules

```kotlin
// Always use ViewBinding — never findViewById
private var _binding: FragmentDashboardBinding? = null
private val binding get() = _binding!!

override fun onCreateView(...): View {
    _binding = FragmentDashboardBinding.inflate(inflater, container, false)
    return binding.root
}

// Always null the binding in onDestroyView to prevent memory leaks
override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
}

// Every Activity that must not appear in recents:
// Add to manifest: android:excludeFromRecents="true"
// Also set flag programmatically where needed:
intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
```

---

## Constants Pattern

```kotlin
// All constants in a single object per module — never magic strings
object Constants {
    const val SUPABASE_TABLE_LOCATION = "child_location"
    const val SUPABASE_TABLE_SOS = "sos_events"
    const val SUPABASE_TABLE_COMMANDS = "remote_commands"
    const val SUPABASE_TABLE_APP_USAGE = "app_usage"

    const val LOCATION_INTERVAL_NORMAL = 10_000L   // 10 seconds
    const val LOCATION_INTERVAL_SOS = 5_000L       // 5 seconds during SOS
    const val LOCATION_INTERVAL_STATIONARY = 30_000L // 30 seconds when not moving

    const val STATIONARY_THRESHOLD_METERS = 20f    // under 20m = stationary
    const val STATIONARY_READING_COUNT = 3         // 3 readings to confirm stationary

    const val NOTIFICATION_ID_SERVICE = 1001
    const val CHANNEL_ID = "sys_service"           // never change this string

    const val SECRET_DIALER_CODE = "*#1234#"       // parent re-entry code
}
```

---

## What to Never Do

- Never use LiveData — always StateFlow or SharedFlow
- Never use !! operator anywhere in production code
- Never import Android classes in domain/ layer
- Never use GlobalScope
- Never use SharedPreferences
- Never hardcode Supabase URL or key — always BuildConfig
- Never put business logic in a Fragment or Activity
- Never skip repeatOnLifecycle when collecting Flow in UI
- Never use a single God ViewModel for multiple screens
