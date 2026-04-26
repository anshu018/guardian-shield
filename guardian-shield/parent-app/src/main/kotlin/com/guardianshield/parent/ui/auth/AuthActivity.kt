package com.guardianshield.parent.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guardianshield.parent.databinding.ActivityAuthBinding
import com.guardianshield.parent.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels()

    // Tracks current phone for OTP verification step
    private var currentPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPhoneScreen()
        setupOtpScreen()
        observeState()
    }

    private fun setupPhoneScreen() {
        binding.btnSendOtp.setOnClickListener {
            val phone = binding.etPhone.text?.toString() ?: ""
            viewModel.sendOtp(phone)
        }
    }

    private fun setupOtpScreen() {
        binding.btnVerifyOtp.setOnClickListener {
            val token = binding.etOtp.text?.toString() ?: ""
            viewModel.verifyOtp(currentPhone, token)
        }

        binding.tvResendOtp.setOnClickListener {
            viewModel.sendOtp(currentPhone)
        }

        binding.tvBackArrow.setOnClickListener {
            binding.viewFlipper.displayedChild = 0
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is AuthUiState.Idle -> {
                        setPhoneScreenLoading(false)
                        setOtpScreenLoading(false)
                    }

                    is AuthUiState.Loading -> {
                        // Show loading on whichever screen is active
                        if (binding.viewFlipper.displayedChild == 0) {
                            setPhoneScreenLoading(true)
                        } else {
                            setOtpScreenLoading(true)
                        }
                    }

                    is AuthUiState.OtpSent -> {
                        currentPhone = state.phone
                        setPhoneScreenLoading(false)
                        binding.tvOtpSubtitle.text =
                            "We sent a 6-digit code to +91${state.phone}"
                        binding.viewFlipper.displayedChild = 1
                        setOtpScreenLoading(false)
                        binding.tvOtpError.visibility = View.GONE
                    }

                    is AuthUiState.AuthSuccess -> {
                        startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                        finish()
                    }

                    is AuthUiState.Error -> {
                        setPhoneScreenLoading(false)
                        setOtpScreenLoading(false)
                        if (binding.viewFlipper.displayedChild == 0) {
                            binding.tvPhoneError.text = state.message
                            binding.tvPhoneError.visibility = View.VISIBLE
                        } else {
                            binding.tvOtpError.text = state.message
                            binding.tvOtpError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun setPhoneScreenLoading(loading: Boolean) {
        binding.progressPhone.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSendOtp.isEnabled = !loading
        if (!loading) binding.tvPhoneError.visibility = View.GONE
    }

    private fun setOtpScreenLoading(loading: Boolean) {
        binding.progressOtp.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnVerifyOtp.isEnabled = !loading
        binding.tvResendOtp.isEnabled = !loading
        if (!loading) binding.tvOtpError.visibility = View.GONE
    }
}
