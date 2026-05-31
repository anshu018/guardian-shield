package com.guardianshield.parent.data.remote

interface AuthRepository {
    suspend fun sendOtp(email: String): Result<Unit>
    suspend fun verifyOtp(email: String, token: String): Result<Unit>
    fun isLoggedIn(): Boolean
    fun getCurrentUserId(): String?
    suspend fun signOut()
}
