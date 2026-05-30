package com.guardianshield.child.data.repository

import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.data.remote.dto.AppUsageDto
import com.guardianshield.child.domain.repository.AppUsageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUsageRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val dataStore: LocationDataStore
) : AppUsageRepository {

    override suspend fun uploadAppUsage(packageName: String, appName: String): Result<Unit> {
        val childId = dataStore.getLastKnownLocation()?.childId ?: ""
        val dto = AppUsageDto(
            childId = childId,
            packageName = packageName,
            appName = appName
        )
        return try {
            supabaseClient.postgrest.from("app_usage").insert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBlockedApps(): List<String> = dataStore.getBlockedPackages()

    override suspend fun saveBlockedApps(packages: List<String>) = dataStore.saveBlockedPackages(packages)
}
