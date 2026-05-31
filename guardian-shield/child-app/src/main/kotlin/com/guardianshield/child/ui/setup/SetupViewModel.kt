package com.guardianshield.child.ui.setup

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.guardianshield.child.admin.GuardianDeviceAdminReceiver
import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.domain.usecases.LinkDeviceUseCase
import com.guardianshield.child.services.AppMonitorService
import com.guardianshield.child.services.EmergencySOSService
import com.guardianshield.child.services.GuardianAccessibilityService
import com.guardianshield.child.services.LocationTrackingService
import com.guardianshield.child.services.ServiceWatchdogWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SetupState(
    val currentStep: Int = 0,
    val isFineLocationGranted: Boolean = false,
    val isBackgroundLocationGranted: Boolean = false,
    val isAccessibilityGranted: Boolean = false,
    val isAdminGranted: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isLinked: Boolean = false,
    val isLinkingLoading: Boolean = false,
    val linkingError: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: LocationDataStore,
    private val linkDeviceUseCase: LinkDeviceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupState())
    val uiState: StateFlow<SetupState> = _uiState.asStateFlow()

    init {
        updatePermissionStates()
    }

    fun updatePermissionStates() {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val bgLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            fineLocation
        }

        val accessibility = isAccessibilityServiceEnabled()
        val admin = isDeviceAdminEnabled()
        val usage = isUsageStatsEnabled()
        val notification = isNotificationListenerEnabled()

        _uiState.value = _uiState.value.copy(
            isFineLocationGranted = fineLocation,
            isBackgroundLocationGranted = bgLocation,
            isAccessibilityGranted = accessibility,
            isAdminGranted = admin,
            isUsageStatsGranted = usage,
            isNotificationGranted = notification
        )
    }

    fun setStep(step: Int) {
        if (step in 0..6) {
            _uiState.value = _uiState.value.copy(currentStep = step)
        }
    }

    fun nextStep() {
        val next = _uiState.value.currentStep + 1
        if (next <= 6) {
            setStep(next)
        }
    }

    fun linkDevice(pin: String, name: String, ageStr: String) {
        val cleanPin = pin.trim()
        val cleanName = name.trim()
        val cleanAgeStr = ageStr.trim()

        if (cleanPin.length != 6 || !cleanPin.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(linkingError = "Please enter a valid 6-digit numeric Family PIN.")
            return
        }

        if (cleanName.isBlank()) {
            _uiState.value = _uiState.value.copy(linkingError = "Please enter the child's name.")
            return
        }

        val age = cleanAgeStr.toIntOrNull()
        if (age == null || age < 1 || age > 17) {
            _uiState.value = _uiState.value.copy(linkingError = "Please enter a valid age between 1 and 17.")
            return
        }

        _uiState.value = _uiState.value.copy(
            isLinkingLoading = true,
            linkingError = null
        )

        viewModelScope.launch {
            linkDeviceUseCase(cleanPin, cleanName, age)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLinked = true,
                        isLinkingLoading = false,
                        linkingError = null,
                        currentStep = 1 // Automatically advance to Step 1 (Location Permission)
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLinkingLoading = false,
                        linkingError = it.message ?: "Failed to link device. Please try again."
                    )
                }
        }
    }

    fun completeSetup() {
        viewModelScope.launch {
            // 1. Mark setup completed in DataStore
            dataStore.markSetupCompleted()

            // 2. Start core background services
            startCoreService(LocationTrackingService::class.java)
            startCoreService(AppMonitorService::class.java)
            startCoreService(EmergencySOSService::class.java)

            // 3. Schedule ServiceWatchdogWorker to run every 15 minutes
            scheduleServiceWatchdogWorker()

            // 4. Disable launcher Activity component to permanently hide it from drawer
            hideLauncherIcon()
        }
    }

    private fun <T> startCoreService(serviceClass: Class<T>) {
        val intent = Intent(context, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun scheduleServiceWatchdogWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ServiceWatchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun hideLauncherIcon() {
        val packageManager = context.packageManager
        // Exact Launcher activity class name as declared in child-app AndroidManifest.xml:
        // com.guardianshield.child.ui.splash.SplashActivity
        val componentName = ComponentName(context, "com.guardianshield.child.ui.splash.SplashActivity")
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedId = ComponentName(context, GuardianAccessibilityService::class.java).flattenToString()
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return settingValue.split(":").any { it.equals(expectedId, ignoreCase = true) }
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    private fun isUsageStatsEnabled(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return listeners.contains(context.packageName)
    }
}
