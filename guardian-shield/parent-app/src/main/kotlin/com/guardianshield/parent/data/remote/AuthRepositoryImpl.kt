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

    // phone arrives as 10 digits — always convert to E.164 internally
    override suspend fun sendOtp(phone: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val e164phone = "+91$phone"
                supabaseClient.auth.signInWith(OTP) {
                    this.phone = e164phone
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun verifyOtp(phone: String, token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val e164phone = "+91$phone"
                supabaseClient.auth.verifyPhoneOtp(
                    type = OtpType.Phone.SMS,
                    phone = e164phone,
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
