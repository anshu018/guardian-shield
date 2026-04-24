package com.guardianshield.child.data.repository

import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.domain.models.ChildLocation
import com.guardianshield.child.domain.repository.LocationRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val locationDataStore: LocationDataStore
) : LocationRepository {

    override fun observeChildLocation(childId: String): Flow<ChildLocation> = flow {
        // TODO: Supabase Realtime subscription — implemented in L10
    }

    override suspend fun uploadLocation(location: ChildLocation): Result<Unit> {
        return try {
            // TODO: Supabase insert — implemented in L6
            locationDataStore.saveLastKnownLocation(location)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCachedLocation(): ChildLocation? {
        return locationDataStore.getLastKnownLocation()
    }
}
