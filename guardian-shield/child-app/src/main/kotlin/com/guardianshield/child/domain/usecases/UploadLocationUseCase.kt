package com.guardianshield.child.domain.usecases

import com.guardianshield.child.domain.models.ChildLocation
import com.guardianshield.child.domain.repository.LocationRepository
import javax.inject.Inject

class UploadLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(location: ChildLocation): Result<Unit> {
        return locationRepository.uploadLocation(location)
    }
}
