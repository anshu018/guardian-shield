package com.guardianshield.child.domain.repository

interface AppUsageRepository {
    suspend fun uploadAppUsage(packageName: String, appName: String): Result<Unit>
    suspend fun getBlockedApps(): List<String>
    suspend fun saveBlockedApps(packages: List<String>)
}
