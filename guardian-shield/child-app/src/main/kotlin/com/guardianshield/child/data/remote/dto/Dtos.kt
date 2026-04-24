package com.guardianshield.child.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChildLocationDto(
    val id: String? = null,
    @SerialName("child_id") val childId: String,
    val lat: Double,
    val lng: Double,
    val battery: Int,
    val accuracy: Float,
    val timestamp: String? = null
)

@Serializable
data class SosEventDto(
    val id: String? = null,
    @SerialName("child_id") val childId: String,
    val lat: Double,
    val lng: Double,
    val active: Boolean = true,
    @SerialName("triggered_at") val triggeredAt: String? = null
)

@Serializable
data class AppUsageDto(
    val id: String? = null,
    @SerialName("child_id") val childId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int = 0
)

@Serializable
data class RemoteCommandDto(
    val id: String? = null,
    @SerialName("child_id") val childId: String,
    val command: String,
    val payload: String? = null,
    val executed: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FamilyDto(
    val id: String,
    @SerialName("family_code") val familyCode: String
)

@Serializable
data class ChildDto(
    val id: String? = null,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val age: Int,
    val phone: String = ""
)
