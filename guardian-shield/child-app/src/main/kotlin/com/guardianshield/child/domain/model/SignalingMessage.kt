package com.guardianshield.child.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SignalingMessage(
    val type: String,               // "offer", "answer", "candidate", "join", "leave", "ready"
    val sender: String? = null,     // "parent" or "child"
    val familyCode: String? = null,
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val candidate: String? = null
)
