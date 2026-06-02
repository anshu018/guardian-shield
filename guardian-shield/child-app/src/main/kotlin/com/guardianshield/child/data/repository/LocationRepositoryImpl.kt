package com.guardianshield.child.data.repository

import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.data.remote.dto.ChildLocationDto
import com.guardianshield.child.domain.models.ChildLocation
import com.guardianshield.child.domain.repository.LocationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val locationDataStore: LocationDataStore
) : LocationRepository {

    override fun observeChildLocation(childId: String): Flow<ChildLocation> = flow {
        // Observers will be hooked into Supabase Realtime in L10
    }

    override suspend fun uploadLocation(location: ChildLocation): Result<Unit> {
        val dto = ChildLocationDto(
            childId = location.childId,
            lat = location.lat,
            lng = location.lng,
            battery = location.battery,
            accuracy = location.accuracy
        )

        // Always save last known locally
        locationDataStore.saveLastKnownLocation(location)

        android.util.Log.d("LocationRepository", "Attempting location upload to Supabase: $dto")
        return try {
            // 1. Attempt to upload the current location
            supabaseClient.postgrest.from("child_location").insert(dto)
            android.util.Log.i("LocationRepository", "Location uploaded successfully to Supabase.")

            // 2. If successful, check for any cached offline locations
            val cachedLocations = locationDataStore.getOfflineLocations()
            if (cachedLocations.isNotEmpty()) {
                android.util.Log.d("LocationRepository", "Uploading ${cachedLocations.size} offline cached locations...")
                for (cached in cachedLocations) {
                    try {
                        supabaseClient.postgrest.from("child_location").insert(cached)
                    } catch (e: Exception) {
                        android.util.Log.e("LocationRepository", "Failed to upload offline cached location: ${e.message}")
                        // If uploading a cached point fails, stop batch upload and keep it in cache
                        return Result.success(Unit)
                    }
                }
                // Clear cache on successful upload of all items
                locationDataStore.clearOfflineLocations()
                android.util.Log.i("LocationRepository", "All offline cached locations uploaded.")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("LocationRepository", "Failed to upload location, caching offline: ${e.message}", e)
            // 3. On failure (e.g. network lost), cache locally
            locationDataStore.saveOfflineLocation(dto)
            Result.failure(e)
        }
    }

    override suspend fun getCachedLocation(): ChildLocation? {
        return locationDataStore.getLastKnownLocation()
    }
}
