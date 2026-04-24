package com.guardianshield.parent.domain.models

data class ChildLocation(
    val childId: String,
    val lat: Double,
    val lng: Double,
    val battery: Int,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class SosEvent(
    val id: String = "",
    val childId: String,
    val lat: Double,
    val lng: Double,
    val active: Boolean = true,
    val triggeredAt: Long = System.currentTimeMillis()
)

enum class CommandType { LOCK, ALARM, BLOCK_APP, MESSAGE }

data class RemoteCommand(
    val id: String,
    val childId: String,
    val command: CommandType,
    val payload: Map<String, String> = emptyMap(),
    val executed: Boolean = false
)

data class Child(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val lastLocation: ChildLocation? = null
)
