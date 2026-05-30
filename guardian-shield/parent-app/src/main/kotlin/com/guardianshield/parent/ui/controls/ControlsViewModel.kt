package com.guardianshield.parent.ui.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.domain.models.CommandType
import com.guardianshield.parent.domain.models.RemoteCommand
import com.guardianshield.parent.domain.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class ControlsUiState {
    object Loading : ControlsUiState()
    data class Success(
        val children: List<Child>,
        val selectedChild: Child?,
        val commandsList: List<RemoteCommand>,
        val errorMsg: String? = null
    ) : ControlsUiState()
    data class Error(val message: String) : ControlsUiState()
}

@HiltViewModel
class ControlsViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val parentDataStore: ParentDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ControlsUiState>(ControlsUiState.Loading)
    val uiState: StateFlow<ControlsUiState> = _uiState.asStateFlow()

    private var childrenList = emptyList<Child>()
    private var selectedChild: Child? = null
    private var commandsList = emptyList<RemoteCommand>()

    private var monitoringJob: Job? = null
    private var commandsJob: Job? = null

    init {
        observeChildrenAndSelection()
    }

    private fun observeChildrenAndSelection() {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            val familyId = parentDataStore.getFamilyId()
            if (familyId == null) {
                parentRepository.fetchAndCacheFamilyId()
            }

            combine(
                parentRepository.observeChildren(),
                parentDataStore.observeSelectedChildId()
            ) { children, selectedId ->
                val selected = children.firstOrNull { it.id == selectedId }
                    ?: children.firstOrNull()
                Pair(children, selected)
            }.collectLatest { (children, selected) ->
                childrenList = children
                selectedChild = selected
                if (selected != null) {
                    observeCommandsForChild(selected.id)
                } else {
                    emitSuccess()
                }
            }
        }
    }

    private fun observeCommandsForChild(childId: String) {
        commandsJob?.cancel()
        commandsJob = viewModelScope.launch {
            parentRepository.observeCommands(childId).collect { list ->
                commandsList = list
                emitSuccess()
            }
        }
    }

    private fun emitSuccess(errorMsg: String? = null) {
        _uiState.value = ControlsUiState.Success(
            children = childrenList,
            selectedChild = selectedChild,
            commandsList = commandsList,
            errorMsg = errorMsg
        )
    }

    fun selectChild(childId: String) {
        viewModelScope.launch {
            parentDataStore.saveSelectedChildId(childId)
        }
    }

    fun sendRemoteCommand(commandType: CommandType, payload: Map<String, String>) {
        val child = selectedChild ?: return
        viewModelScope.launch {
            val cmd = RemoteCommand(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                command = commandType,
                payload = payload,
                executed = false
            )
            parentRepository.sendCommand(cmd)
                .onFailure {
                    emitSuccess(errorMsg = "Failed to transmit command: ${it.message}")
                }
        }
    }
}
