package com.guardianshield.child.ui.setup

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guardianshield.child.admin.GuardianDeviceAdminReceiver
import com.guardianshield.child.databinding.ActivitySetupBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val viewModel: SetupViewModel by viewModels()

    // 1a. Fine/Coarse location permissions launcher
    private val locationPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        viewModel.updatePermissionStates()
        if (fineGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Toast.makeText(
                    this,
                    "Precision Location Granted. Please grant Background Location next.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                viewModel.setStep(1)
            }
        } else {
            Toast.makeText(
                this,
                "Precision Location is required for Guardian Shield tracking.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 1b. Background location permission launcher
    private val bgLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updatePermissionStates()
        if (granted) {
            Toast.makeText(this, "Background Location Granted.", Toast.LENGTH_SHORT).show()
            viewModel.setStep(1)
        } else {
            Toast.makeText(
                this,
                "Background Location is required to protect this device when closed.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModelState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.updatePermissionStates()
        autoAdvanceIfNeeded()
    }

    private fun setupClickListeners() {
        // Device Linking Link Button Click
        binding.btnLink.setOnClickListener {
            val pin = binding.etPin.text?.toString() ?: ""
            val name = binding.etChildName.text?.toString() ?: ""
            val age = binding.etChildAge.text?.toString() ?: ""
            viewModel.linkDevice(pin, name, age)
        }

        // Location Permission Click
        binding.btnLocation.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.isFineLocationGranted) {
                locationPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else if (!state.isBackgroundLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        // Accessibility Service Click
        binding.btnAccessibility.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.isAccessibilityGranted) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        // Device Admin Click
        binding.btnAdmin.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.isAdminGranted) {
                val adminComponent = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Protects background safety services from unauthorized uninstallation."
                    )
                }
                startActivity(intent)
            }
        }

        // Usage Stats Click
        binding.btnUsage.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.isUsageStatsGranted) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        // Notification Listener Click
        binding.btnNotification.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.isNotificationGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        // Activate Stealth Protection Click
        binding.btnActivate.setOnClickListener {
            viewModel.completeSetup()
            Toast.makeText(
                this,
                "Stealth Active: Services running, launcher hidden.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

        // Manual Next Step Fallback Button
        binding.btnNext.setOnClickListener {
            viewModel.nextStep()
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUiState(state)
                }
            }
        }
    }

    private fun updateUiState(state: SetupState) {
        // Update ViewFlipper display child and ProgressBar tracking
        binding.viewFlipper.displayedChild = state.currentStep
        binding.progressBar.progress = state.currentStep + 1
        binding.tvStepIndicator.text = "Step ${state.currentStep + 1} of 7"

        // Step-specific visual elements updating
        when (state.currentStep) {
            0 -> {
                // Device Linking step
                binding.btnLink.isEnabled = !state.isLinkingLoading
                binding.etPin.isEnabled = !state.isLinkingLoading
                binding.etChildName.isEnabled = !state.isLinkingLoading
                binding.etChildAge.isEnabled = !state.isLinkingLoading

                binding.progressLinking.visibility = if (state.isLinkingLoading) View.VISIBLE else View.GONE

                if (state.linkingError != null) {
                    binding.tvLinkingError.text = state.linkingError
                    binding.tvLinkingError.visibility = View.VISIBLE
                } else {
                    binding.tvLinkingError.visibility = View.GONE
                }
            }
            1 -> {
                // Precision location indicator
                if (state.isFineLocationGranted) {
                    binding.tvLocationState.text = "Granted"
                    binding.tvLocationState.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                    binding.tvLocationState.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                } else {
                    binding.tvLocationState.text = "Required"
                    binding.tvLocationState.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                }

                // Background location indicator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    binding.cardBackgroundLocation.visibility = View.VISIBLE
                    binding.tvLocationExplain.visibility = View.VISIBLE
                    if (state.isBackgroundLocationGranted) {
                        binding.tvBackgroundLocationState.text = "Granted"
                        binding.tvBackgroundLocationState.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                        binding.tvBackgroundLocationState.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                    } else if (state.isFineLocationGranted) {
                        binding.tvBackgroundLocationState.text = "Required"
                        binding.tvBackgroundLocationState.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                    } else {
                        binding.tvBackgroundLocationState.text = "Locked"
                    }
                } else {
                    binding.cardBackgroundLocation.visibility = View.GONE
                    binding.tvLocationExplain.visibility = View.GONE
                }

                // Action button text updating
                if (!state.isFineLocationGranted) {
                    binding.btnLocation.text = "Grant Precision Location"
                    binding.btnLocation.isEnabled = true
                } else if (!state.isBackgroundLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    binding.btnLocation.text = "Grant Background Location"
                    binding.btnLocation.isEnabled = true
                } else {
                    binding.btnLocation.text = "Location Configured"
                    binding.btnLocation.isEnabled = false
                }
            }
            2 -> {
                // Accessibility indicator
                if (state.isAccessibilityGranted) {
                    binding.tvAccessibilityState.text = "Active"
                    binding.tvAccessibilityState.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                    binding.tvAccessibilityState.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                    binding.btnAccessibility.text = "Accessibility Active"
                    binding.btnAccessibility.isEnabled = false
                } else {
                    binding.tvAccessibilityState.text = "Inactive"
                    binding.btnAccessibility.text = "Activate Service"
                    binding.btnAccessibility.isEnabled = true
                }
            }
            3 -> {
                // Device Admin indicator
                if (state.isAdminGranted) {
                    binding.tvAdminState.text = "Active"
                    binding.tvAdminState.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                    binding.tvAdminState.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                    binding.btnAdmin.text = "Device Admin Active"
                    binding.btnAdmin.isEnabled = false
                } else {
                    binding.tvAdminState.text = "Inactive"
                    binding.btnAdmin.text = "Activate Administrator"
                    binding.btnAdmin.isEnabled = true
                }
            }
            4 -> {
                // Usage Stats indicator
                if (state.isUsageStatsGranted) {
                    binding.tvUsageState.text = "Allowed"
                    binding.tvUsageState.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                    binding.tvUsageState.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                    binding.btnUsage.text = "Usage Access Active"
                    binding.btnUsage.isEnabled = false
                } else {
                    binding.tvUsageState.text = "Disabled"
                    binding.btnUsage.text = "Grant Usage Access"
                    binding.btnUsage.isEnabled = true
                }
            }
            5 -> {
                // Notification access indicator
                if (state.isNotificationGranted) {
                    binding.tvNotificationState.text = "Allowed"
                    binding.tvNotificationState.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                    binding.tvNotificationState.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                    binding.btnNotification.text = "Notification Access Active"
                    binding.btnNotification.isEnabled = false
                } else {
                    binding.tvNotificationState.text = "Disabled"
                    binding.btnNotification.text = "Grant Notification Access"
                    binding.btnNotification.isEnabled = true
                }
            }
            6 -> {
                // Step 7 summary report card updating
                updateReportCard(state)
            }
        }

        // Show/hide Next button fallback in footer
        val canAdvance = when (state.currentStep) {
            0 -> state.isLinked
            1 -> state.isFineLocationGranted && state.isBackgroundLocationGranted
            2 -> state.isAccessibilityGranted
            3 -> state.isAdminGranted
            4 -> state.isUsageStatsGranted
            5 -> state.isNotificationGranted
            else -> false
        }
        binding.btnNext.visibility = if (canAdvance && state.currentStep < 6) View.VISIBLE else View.GONE
    }

    private fun updateReportCard(state: SetupState) {
        binding.tvReportLocation.visibility = if (state.isFineLocationGranted && state.isBackgroundLocationGranted) View.VISIBLE else View.GONE
        binding.tvReportAccessibility.visibility = if (state.isAccessibilityGranted) View.VISIBLE else View.GONE
        binding.tvReportAdmin.visibility = if (state.isAdminGranted) View.VISIBLE else View.GONE
        binding.tvReportUsage.visibility = if (state.isUsageStatsGranted) View.VISIBLE else View.GONE
        binding.tvReportNotification.visibility = if (state.isNotificationGranted) View.VISIBLE else View.GONE

        // Enable activate button only if all prerequisites are fully met
        val allSystemsSecure = state.isFineLocationGranted &&
                state.isBackgroundLocationGranted &&
                state.isAccessibilityGranted &&
                state.isAdminGranted &&
                state.isUsageStatsGranted &&
                state.isNotificationGranted

        binding.btnActivate.isEnabled = allSystemsSecure
    }

    private fun autoAdvanceIfNeeded() {
        val state = viewModel.uiState.value
        when (state.currentStep) {
            0 -> {
                // Step 0 explicitly excluded from auto-advance logic.
            }
            1 -> {
                if (state.isFineLocationGranted && state.isBackgroundLocationGranted) {
                    viewModel.setStep(2)
                }
            }
            2 -> {
                if (state.isAccessibilityGranted) {
                    viewModel.setStep(3)
                }
            }
            3 -> {
                if (state.isAdminGranted) {
                    viewModel.setStep(4)
                }
            }
            4 -> {
                if (state.isUsageStatsGranted) {
                    viewModel.setStep(5)
                }
            }
            5 -> {
                if (state.isNotificationGranted) {
                    viewModel.setStep(6)
                }
            }
        }
    }
}
