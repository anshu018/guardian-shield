package com.guardianshield.child.domain.usecases

import com.guardianshield.child.domain.repository.LinkRepository
import javax.inject.Inject

class LinkDeviceUseCase @Inject constructor(
    private val repository: LinkRepository
) {
    suspend operator fun invoke(pin: String, name: String, age: Int): Result<String> {
        return repository.verifyAndLink(pin, name, age)
    }
}
