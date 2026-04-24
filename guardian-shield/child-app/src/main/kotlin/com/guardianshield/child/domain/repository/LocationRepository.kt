package com.guardianshield.child.domain.repository

import com.guardianshield.child.domain.models.ChildLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeChildLocation(childId: String): Flow<ChildLocation>
    suspend fun uploadLocation(location: ChildLocation): Result<Unit>
    suspend fun getCachedLocation(): ChildLocation?
}
