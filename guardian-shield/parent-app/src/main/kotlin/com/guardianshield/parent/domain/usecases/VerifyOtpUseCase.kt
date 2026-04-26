package com.guardianshield.parent.domain.usecases

import com.guardianshield.parent.data.remote.AuthRepository
import javax.inject.Inject

class VerifyOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    // Validates 6-digit OTP before calling repository
    suspend operator fun invoke(phone: String, token: String): Result<Unit> {
        if (token.length != 6 || !token.all { it.isDigit() }) {
            return Result.failure(Exception("Enter the 6-digit OTP sent to your number"))
        }
        return authRepository.verifyOtp(phone, token)
    }
}
