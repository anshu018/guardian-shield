package com.guardianshield.parent.data.remote.dto

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
    @SerialName("active") val active: Boolean = true,
    @SerialName("triggered_at") val triggeredAt: String? = null
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
    val id: String? = null,
    @SerialName("family_code") val familyCode: String
)

@Serializable
data class ChildDto(
    val id: String? = null,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val age: Int,
    val phone: String? = null
)

@Serializable
data class ParentDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val phone: String
)

@Serializable
data class CallLogDto(
    val id: Long? = null,
    @SerialName("child_id") val childId: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("call_type") val callType: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SmsPreviewDto(
    val id: Long? = null,
    @SerialName("child_id") val childId: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("message_body") val messageBody: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ContactProfileDto(
    val id: Long? = null,
    @SerialName("child_id") val childId: String,
    val name: String,
    @SerialName("phone_number") val phoneNumber: String
)

