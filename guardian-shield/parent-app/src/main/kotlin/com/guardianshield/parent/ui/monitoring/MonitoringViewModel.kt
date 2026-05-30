package com.guardianshield.parent.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.domain.models.CallLog
import com.guardianshield.parent.domain.models.SmsPreview
import com.guardianshield.parent.domain.models.ContactProfile
import com.guardianshield.parent.domain.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MonitoringUiState {
    object Loading : MonitoringUiState()
    data class Success(
        val children: List<Child>,
        val selectedChild: Child?,
        val callLogs: List<CallLog>,
        val smsPreviews: List<SmsPreview>,
        val contacts: List<ContactProfile>
    ) : MonitoringUiState()
    data class Error(val message: String) : MonitoringUiState()
}

@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val parentDataStore: ParentDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<MonitoringUiState>(MonitoringUiState.Loading)
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private var childrenList = emptyList<Child>()
    private var selectedChild: Child? = null

    private var selectionJob: Job? = null
    private var callLogsJob: Job? = null
    private var smsJob: Job? = null
    private var contactsJob: Job? = null

    init {
        observeChildrenAndSelection()
    }

    private fun observeChildrenAndSelection() {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            // First load family ID if needed
            val familyId = parentDataStore.getFamilyId()
            if (familyId == null) {
                parentRepository.fetchAndCacheFamilyId()
            }

            // Combine children list and selection
            parentRepository.observeChildren().collectLatest { children ->
                childrenList = children
                parentDataStore.observeSelectedChildId().collectLatest { selectedId ->
                    selectedChild = children.firstOrNull { it.id == selectedId } ?: children.firstOrNull()
                    
                    if (selectedChild == null) {
                        if (children.isEmpty()) {
                            _uiState.value = MonitoringUiState.Success(
                                children = emptyList(),
                                selectedChild = null,
                                callLogs = emptyList(),
                                smsPreviews = emptyList(),
                                contacts = emptyList()
                            )
                        } else {
                            // If none selected but children exist, save first child as selected
                            parentDataStore.saveSelectedChildId(children.first().id)
                        }
                    } else {
                        // Observe telemetry for selected child
                        observeTelemetry(selectedChild!!.id)
                    }
                }
            }
        }
    }

    private fun observeTelemetry(childId: String) {
        // Cancel previous sync jobs to avoid leaks
        callLogsJob?.cancel()
        smsJob?.cancel()
        contactsJob?.cancel()

        val currentCallLogs = mutableListOf<CallLog>()
        val currentSmsPreviews = mutableListOf<SmsPreview>()
        val currentContacts = mutableListOf<ContactProfile>()

        fun emitSuccess() {
            _uiState.value = MonitoringUiState.Success(
                children = childrenList,
                selectedChild = selectedChild,
                callLogs = currentCallLogs.toList(),
                smsPreviews = currentSmsPreviews.toList(),
                contacts = currentContacts.toList()
            )
        }

        callLogsJob = viewModelScope.launch {
            parentRepository.observeCallLogs(childId).collect { logs ->
                currentCallLogs.clear()
                currentCallLogs.addAll(logs)
                emitSuccess()
            }
        }

        smsJob = viewModelScope.launch {
            parentRepository.observeSmsPreviews(childId).collect { sms ->
                currentSmsPreviews.clear()
                currentSmsPreviews.addAll(sms)
                emitSuccess()
            }
        }

        contactsJob = viewModelScope.launch {
            parentRepository.observeContacts(childId).collect { contacts ->
                currentContacts.clear()
                currentContacts.addAll(contacts)
                emitSuccess()
            }
        }
    }

    fun selectChild(childId: String) {
        viewModelScope.launch {
            parentDataStore.saveSelectedChildId(childId)
        }
    }
}
