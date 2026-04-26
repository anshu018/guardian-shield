package com.guardianshield.parent.domain.usecases

import com.guardianshield.parent.data.remote.AuthRepository
import javax.inject.Inject

class SendOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    // Validates 10-digit Indian phone number before calling repository
    // Returns Result.failure with clear message if format is wrong
    suspend operator fun invoke(phone: String): Result<Unit> {
        if (phone.length != 10 || !phone.all { it.isDigit() }) {
            return Result.failure(Exception("Enter a valid 10-digit Indian mobile number"))
        }
        return authRepository.sendOtp(phone)
    }
}
