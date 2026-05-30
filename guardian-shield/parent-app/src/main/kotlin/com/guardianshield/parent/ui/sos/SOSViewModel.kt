package com.guardianshield.parent.ui.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianshield.parent.domain.models.SosEvent
import com.guardianshield.parent.domain.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SosUiState {
    object Idle : SosUiState()
    data class ChildDetails(val name: String, val phone: String) : SosUiState()
}

@HiltViewModel
class SOSViewModel @Inject constructor(
    private val repository: ParentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SosUiState>(SosUiState.Idle)
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    private val _sosHistory = MutableStateFlow<List<SosEvent>>(emptyList())
    val sosHistory: StateFlow<List<SosEvent>> = _sosHistory.asStateFlow()

    private val _resolveStatus = MutableStateFlow<Result<Unit>?>(null)
    val resolveStatus: StateFlow<Result<Unit>?> = _resolveStatus.asStateFlow()

    fun loadChildDetails(childId: String) {
        viewModelScope.launch {
            repository.observeChildren().collect { children ->
                val child = children.firstOrNull { it.id == childId }
                if (child != null) {
                    _uiState.value = SosUiState.ChildDetails(child.name, child.phone)
                }
            }
        }
    }

    fun loadSosHistory(childId: String) {
        viewModelScope.launch {
            repository.observeSosHistory(childId).collect { history ->
                _sosHistory.value = history
            }
        }
    }

    fun resolveSos(eventId: String) {
        viewModelScope.launch {
            val result = repository.resolveSosEvent(eventId)
            _resolveStatus.value = result
        }
    }
}
