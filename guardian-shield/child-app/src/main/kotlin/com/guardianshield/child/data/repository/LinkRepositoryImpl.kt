package com.guardianshield.child.data.repository

import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.data.remote.dto.FamilyDto
import com.guardianshield.child.data.remote.dto.ParentDto
import com.guardianshield.child.data.remote.dto.ChildDto
import com.guardianshield.child.domain.models.ChildLocation
import com.guardianshield.child.domain.repository.LinkRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
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

                // 1. Query families WHERE family_code matches the PIN
                val family = supabaseClient.postgrest.from("families")
                    .select {
                        filter {
                            eq("family_code", pin)
                        }
                    }.decodeSingleOrNull<FamilyDto>()
                    ?: return@withContext Result.failure(Exception("Invalid code. Ask the parent to check their app."))

                val familyId = family.id ?: return@withContext Result.failure(Exception("Registered family is missing a valid identifier."))

                // 2. Query parents WHERE family_id matches the family
                val parent = supabaseClient.postgrest.from("parents")
                    .select {
                        filter {
                            eq("family_id", familyId)
                        }
                    }.decodeList<ParentDto>().firstOrNull()

                val parentPhone = parent?.phone ?: "+919876543210"

                // 3. Insert new row into children
                val newChild = ChildDto(
                    familyId = familyId,
                    name = name,
                    age = age,
                    phone = ""
                )
                val insertedChild = supabaseClient.postgrest.from("children")
                    .insert(newChild) {
                        select()
                    }.decodeSingle<ChildDto>()

                val childId = insertedChild.id ?: return@withContext Result.failure(Exception("Failed to register child profile on database."))

                // 4. Persist child_id, parent_phone, and linking_pin to DataStore
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
