package com.guardianshield.parent.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianshield.parent.data.remote.AuthRepository
import com.guardianshield.parent.domain.usecases.SendOtpUseCase
import com.guardianshield.parent.domain.usecases.VerifyOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class OtpSent(val email: String) : AuthUiState()
    object AuthSuccess : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sendOtpUseCase: SendOtpUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val authRepository: AuthRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun sendOtp(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            sendOtpUseCase(email)
                .onSuccess { _uiState.value = AuthUiState.OtpSent(email) }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Failed to send OTP") }
        }
    }

    fun verifyOtp(email: String, token: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            verifyOtpUseCase(email, token)
                .onSuccess { _uiState.value = AuthUiState.AuthSuccess }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Invalid OTP") }
        }
    }

    fun signInWithPassword(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                supabaseClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                _uiState.value = AuthUiState.AuthSuccess
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()
}
