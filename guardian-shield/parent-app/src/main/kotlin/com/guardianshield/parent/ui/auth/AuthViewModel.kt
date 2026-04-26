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
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class OtpSent(val phone: String) : AuthUiState()
    object AuthSuccess : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sendOtpUseCase: SendOtpUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun sendOtp(phone: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            sendOtpUseCase(phone)
                .onSuccess { _uiState.value = AuthUiState.OtpSent(phone) }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Failed to send OTP") }
        }
    }

    fun verifyOtp(phone: String, token: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            verifyOtpUseCase(phone, token)
                .onSuccess { _uiState.value = AuthUiState.AuthSuccess }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Invalid OTP") }
        }
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()
}
