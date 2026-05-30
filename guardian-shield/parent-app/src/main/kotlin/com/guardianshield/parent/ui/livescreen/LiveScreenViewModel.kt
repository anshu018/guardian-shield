package com.guardianshield.parent.ui.livescreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.data.remote.SignalingClient
import com.guardianshield.parent.domain.model.SignalingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LiveScreenState {
    object Idle : LiveScreenState
    object Connecting : LiveScreenState
    object Buffering : LiveScreenState
    data class Streaming(
        val fps: Int = 15,
        val width: Int = 360,
        val height: Int = 640,
        val bandwidthQuality: BandwidthQuality = BandwidthQuality.EXCELLENT
    ) : LiveScreenState
    data class Error(val message: String) : LiveScreenState
}

enum class BandwidthQuality {
    EXCELLENT, GOOD, POOR, CRITICAL
}

@HiltViewModel
class LiveScreenViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val parentDataStore: ParentDataStore
) : ViewModel() {

    val connectionState: StateFlow<SignalingClient.ConnectionState> = signalingClient.connectionState
    val incomingSignals: SharedFlow<SignalingMessage> = signalingClient.incomingSignals

    private val _streamState = MutableStateFlow<LiveScreenState>(LiveScreenState.Idle)
    val streamState: StateFlow<LiveScreenState> = _streamState.asStateFlow()

    fun updateStreamState(state: LiveScreenState) {
        _streamState.value = state
    }

    fun startSignaling() {
        viewModelScope.launch {
            val familyId = parentDataStore.getFamilyId()
            if (familyId != null) {
                signalingClient.connect(familyId)
            }
        }
    }

    fun sendSignal(message: SignalingMessage) {
        signalingClient.sendSignal(message)
    }

    fun stopSignaling() {
        signalingClient.disconnect()
        updateStreamState(LiveScreenState.Idle)
    }

    override fun onCleared() {
        stopSignaling()
        super.onCleared()
    }
}
