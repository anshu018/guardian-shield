package com.guardianshield.parent.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.domain.models.ChildLocation
import com.guardianshield.parent.domain.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Empty(val familyCode: String) : DashboardUiState()
    data class Success(val children: List<Child>, val selectedChild: Child) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val parentDataStore: ParentDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _childHistory = MutableStateFlow<List<ChildLocation>>(emptyList())
    val childHistory: StateFlow<List<ChildLocation>> = _childHistory.asStateFlow()

    private var childrenCollectJob: Job? = null
    private var historyCollectJob: Job? = null

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            parentDataStore.saveSelectedChildId("40ba2e04-a40b-44ba-b7d1-f23497364929")
            parentRepository.getOrCreateFamilyCode()
                .onSuccess { familyCode ->
                    startMonitoring(familyCode)
                }
                .onFailure { _uiState.value = DashboardUiState.Error(it.message ?: "Failed to load family credentials.") }
        }
    }

    private fun startMonitoring(familyCode: String) {
        childrenCollectJob?.cancel()
        childrenCollectJob = viewModelScope.launch {
            parentRepository.observeChildren().collectLatest { children ->
                if (children.isEmpty()) {
                    _uiState.value = DashboardUiState.Empty(familyCode)
                } else {
                    parentDataStore.observeSelectedChildId().collectLatest { selectedId ->
                        val activeChild = children.firstOrNull { it.id == selectedId }
                            ?: children.firstOrNull { it.id == "40ba2e04-a40b-44ba-b7d1-f23497364929" }
                            ?: children.first()
                        _uiState.value = DashboardUiState.Success(children, activeChild)

                        // Track changes to history of the focused child
                        observeHistory(activeChild.id)
                    }
                }
            }
        }
    }

    private fun observeHistory(childId: String) {
        historyCollectJob?.cancel()
        historyCollectJob = viewModelScope.launch {
            parentRepository.observeChildLocationHistory(childId).collect { history ->
                _childHistory.value = history
            }
        }
    }
}
