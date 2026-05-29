package com.guardianshield.child.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChildLocationDto(
    val id: Long? = null,
    @SerialName("child_id") val childId: String,
    @SerialName("latitude") val lat: Double,
    @SerialName("longitude") val lng: Double,
    @SerialName("battery_percentage") val battery: Int,
    @SerialName("accuracy_radius") val accuracy: Float,
    @SerialName("created_at") val timestamp: String? = null
)

@Serializable
data class SosEventDto(
    val id: String? = null,
    @SerialName("child_id") val childId: String,
    @SerialName("latitude") val lat: Double,
    @SerialName("longitude") val lng: Double,
    @SerialName("is_active") val active: Boolean = true,
    @SerialName("triggered_at") val triggeredAt: String? = null
)

@Serializable
data class AppUsageDto(
    val id: Long? = null,
    @SerialName("child_id") val childId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null
)

@Serializable
data class RemoteCommandDto(
    val id: String? = null,
    @SerialName("child_id") val childId: String,
    @SerialName("command_type") val command: String,
    val payload: String? = null,
    @SerialName("is_executed") val executed: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("executed_at") val executedAt: String? = null
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
