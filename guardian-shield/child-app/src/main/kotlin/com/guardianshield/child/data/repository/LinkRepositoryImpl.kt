package com.guardianshield.child.data.repository

import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.data.remote.dto.FamilyDto
import com.guardianshield.child.data.remote.dto.ParentDto
import com.guardianshield.child.data.remote.dto.ChildDto
import com.guardianshield.child.domain.models.ChildLocation
import com.guardianshield.child.domain.repository.LinkRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val dataStore: LocationDataStore
) : LinkRepository {

    override suspend fun isAlreadyLinked(): Boolean {
        val currentChildId = dataStore.getChildId()
        return !currentChildId.isNullOrBlank()
    }

    override suspend fun verifyAndLink(pin: String, name: String, age: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (isAlreadyLinked()) {
                    val childId = dataStore.getChildId() ?: ""
                    return@withContext Result.success(childId)
                }

                // 1. Invoke the secure register-child Edge Function
                val response = supabaseClient.functions.invoke(
                    function = "register-child",
                    body = buildJsonObject {
                        put("family_code", pin)
                        put("name", name)
                        put("age", age)
                    }
                )

                val registerData = response.body<RegisterChildResponse>()
                if (!registerData.success) {
                    return@withContext Result.failure(Exception("Registration failed: secure function reported failure."))
                }

                val childId = registerData.childId
                val familyId = registerData.familyId

                // 2. Query parents WHERE family_id matches the family
                val parent = supabaseClient.postgrest.from("parents")
                    .select {
                        filter {
                            eq("family_id", familyId)
                        }
                    }.decodeList<ParentDto>().firstOrNull()

                val parentPhone = parent?.phone ?: "+919876543210"

                // 3. Persist child_id, parent_phone, and linking_pin to DataStore
                dataStore.saveFamilyId(familyId)
                dataStore.saveChildId(childId)
                dataStore.saveParentPhone(parentPhone)
                dataStore.saveLinkingPin(pin)
                dataStore.saveChildName(name)
                
                // Seed child location in DataStore so services can immediately load the child ID
                dataStore.saveLastKnownLocation(
                    ChildLocation(
                        childId = childId,
                        lat = 0.0,
                        lng = 0.0,
                        battery = 100,
                        accuracy = 0f,
                        timestamp = System.currentTimeMillis()
                    )
                )

                Result.success(childId)
            } catch (e: Exception) {
                Result.failure(Exception("Handshake failed: ${e.localizedMessage ?: "Check your network connection"}", e))
            }
        }
}

@Serializable
data class RegisterChildResponse(
    val success: Boolean,
    @SerialName("child_id") val childId: String,
    @SerialName("family_id") val familyId: String
)
