package com.guardianshield.parent.domain.usecases

import com.guardianshield.parent.data.remote.AuthRepository
import javax.inject.Inject

class SendOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    // Validates email address format before calling repository
    // Returns Result.failure with clear message if format is wrong
    suspend operator fun invoke(email: String): Result<Unit> {
        if (!email.contains("@") || !email.contains(".") || email.length < 5) {
            return Result.failure(Exception("Enter a valid email address"))
        }
        return authRepository.sendOtp(email)
    }
}
