package com.guardianshield.parent.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.OtpType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : AuthRepository {

    override suspend fun sendOtp(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabaseClient.auth.signInWith(OTP) {
                    this.email = email
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun verifyOtp(email: String, token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabaseClient.auth.verifyEmailOtp(
                    type = OtpType.Email.EMAIL,
                    email = email,
                    token = token
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun isLoggedIn(): Boolean =
        supabaseClient.auth.currentSessionOrNull() != null

    override fun getCurrentUserId(): String? =
        supabaseClient.auth.currentSessionOrNull()?.user?.id

    override suspend fun signOut() {
        try {
            supabaseClient.auth.signOut()
        } catch (e: Exception) {
            // Session already invalid — treat as signed out
        }
    }
}
