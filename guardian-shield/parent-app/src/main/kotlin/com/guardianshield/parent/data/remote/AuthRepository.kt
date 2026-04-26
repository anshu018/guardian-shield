package com.guardianshield.parent.data.remote

interface AuthRepository {
    suspend fun sendOtp(phone: String): Result<Unit>
    suspend fun verifyOtp(phone: String, token: String): Result<Unit>
    fun isLoggedIn(): Boolean
    fun getCurrentUserId(): String?
    suspend fun signOut()
}
