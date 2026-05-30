package com.guardianshield.parent.data.repository

import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.data.remote.dto.ChildDto
import com.guardianshield.parent.data.remote.dto.ChildLocationDto
import com.guardianshield.parent.data.remote.dto.ParentDto
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.domain.models.ChildLocation
import com.guardianshield.parent.domain.models.RemoteCommand
import com.guardianshield.parent.domain.models.SosEvent
import com.guardianshield.parent.domain.models.CallLog
import com.guardianshield.parent.domain.models.SmsPreview
import com.guardianshield.parent.domain.models.ContactProfile
import com.guardianshield.parent.data.remote.dto.CallLogDto
import com.guardianshield.parent.data.remote.dto.SmsPreviewDto
import com.guardianshield.parent.data.remote.dto.ContactProfileDto
import com.guardianshield.parent.domain.repository.ParentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class ParentRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val dataStore: ParentDataStore
) : ParentRepository {

    override suspend fun fetchAndCacheFamilyId(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentSessionOrNull()?.user?.id
                ?: return@withContext Result.failure(Exception("Session invalid. Log in again."))

            val parentDto = supabaseClient.postgrest.from("parents")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeSingleOrNull<ParentDto>()

            if (parentDto != null) {
                dataStore.saveFamilyId(parentDto.familyId)
                dataStore.setLoggedIn(true)
                Result.success(parentDto.familyId)
            } else {
                Result.failure(Exception("Parent profile not found in database."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeChildren(): Flow<List<Child>> = flow {
        val familyId = dataStore.getFamilyId() ?: return@flow

        // 1. Fetch current children linked to the family
        val childDtos = supabaseClient.postgrest.from("children")
            .select {
                filter {
                    eq("family_id", familyId)
                }
            }.decodeList<ChildDto>()

        // 2. Fetch latest location coordinates for each child initially
        val childrenList = childDtos.map { dto ->
            val lastLocDto = supabaseClient.postgrest.from("child_location")
                .select {
                    filter {
                        eq("child_id", dto.id!!)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    limit(count = 1)
                }.decodeList<ChildLocationDto>().firstOrNull()

            val lastLoc = lastLocDto?.let {
                ChildLocation(
                    childId = it.childId,
                    lat = it.lat,
                    lng = it.lng,
                    battery = it.battery,
                    accuracy = it.accuracy,
                    timestamp = System.currentTimeMillis()
                )
            }

            Child(
                id = dto.id!!,
                name = dto.name,
                isOnline = lastLoc?.let { System.currentTimeMillis() - it.timestamp < 30_000 } ?: false,
                lastLocation = lastLoc
            )
        }

        emit(childrenList)

        // 3. Subscribe to real-time location inserts/updates via Supabase WebSockets
        val channel = supabaseClient.realtime.channel("child_location_stream")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "child_location"
        }
        channel.subscribe()

        val childrenMap = childrenList.associateBy { it.id }.toMutableMap()

        changeFlow.collect { action ->
            if (action is PostgresAction.Insert || action is PostgresAction.Update) {
                val record = action.record
                val locDto = Json.decodeFromJsonElement<ChildLocationDto>(record)
                
                if (childrenMap.containsKey(locDto.childId)) {
                    val updatedLoc = ChildLocation(
                        childId = locDto.childId,
                        lat = locDto.lat,
                        lng = locDto.lng,
                        battery = locDto.battery,
                        accuracy = locDto.accuracy,
                        timestamp = System.currentTimeMillis()
                    )
                    val child = childrenMap[locDto.childId]!!
                    childrenMap[locDto.childId] = child.copy(
                        lastLocation = updatedLoc,
                        isOnline = true
                    )
                    emit(childrenMap.values.toList())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun observeChildLocationHistory(childId: String): Flow<List<ChildLocation>> = flow {
        // 1. Initially load the last 10 location updates
        val initialDtos = supabaseClient.postgrest.from("child_location")
            .select {
                filter {
                    eq("child_id", childId)
                }
                order(column = "created_at", order = Order.DESCENDING)
                limit(count = 10)
            }.decodeList<ChildLocationDto>()

        val historyList = initialDtos.map {
            ChildLocation(
                childId = it.childId,
                lat = it.lat,
                lng = it.lng,
                battery = it.battery,
                accuracy = it.accuracy,
                timestamp = System.currentTimeMillis()
            )
        }.reversed().toMutableList()

        emit(historyList.toList())

        // 2. Stream subsequent inserts for this child in real time
        val channel = supabaseClient.realtime.channel("child_history_stream_$childId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "child_location"
        }
        channel.subscribe()

        changeFlow.collect { action ->
            if (action is PostgresAction.Insert) {
                val record = action.record
                val locDto = Json.decodeFromJsonElement<ChildLocationDto>(record)
                
                if (locDto.childId == childId) {
                    val newLoc = ChildLocation(
                        childId = locDto.childId,
                        lat = locDto.lat,
                        lng = locDto.lng,
                        battery = locDto.battery,
                        accuracy = locDto.accuracy,
                        timestamp = System.currentTimeMillis()
                    )
                    historyList.add(newLoc)
                    if (historyList.size > 10) {
                        historyList.removeAt(0)
                    }
                    emit(historyList.toList())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun observeSosEvents(): Flow<SosEvent> = flow {
        // Will be wired in Layer 14
    }

    override suspend fun sendCommand(cmd: RemoteCommand): Result<Unit> {
        return Result.success(Unit) // Will be wired in Layer 13
    }

    override fun observeCallLogs(childId: String, limit: Int): Flow<List<CallLog>> = flow {
        // 1. Initial fetch from database
        val dtos = supabaseClient.postgrest.from("call_logs")
            .select {
                filter {
                    eq("child_id", childId)
                }
                order(column = "created_at", order = Order.DESCENDING)
                limit(count = limit.toLong())
            }.decodeList<CallLogDto>()

        val list = dtos.map {
            CallLog(
                id = it.id ?: 0L,
                childId = it.childId,
                phoneNumber = it.phoneNumber,
                contactName = it.contactName,
                callType = it.callType,
                durationSeconds = it.durationSeconds,
                timestamp = it.createdAt?.let { dateStr -> parseIsoToTimestamp(dateStr) } ?: System.currentTimeMillis()
            )
        }.toMutableList()

        emit(list.toList())

        // 2. Real-time subscription stream
        val channel = supabaseClient.realtime.channel("call_logs_stream_$childId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "call_logs"
        }
        channel.subscribe()

        changeFlow.collect { action ->
            if (action is PostgresAction.Insert) {
                val dto = Json.decodeFromJsonElement<CallLogDto>(action.record)
                if (dto.childId == childId) {
                    val newLog = CallLog(
                        id = dto.id ?: 0L,
                        childId = dto.childId,
                        phoneNumber = dto.phoneNumber,
                        contactName = dto.contactName,
                        callType = dto.callType,
                        durationSeconds = dto.durationSeconds,
                        timestamp = dto.createdAt?.let { dateStr -> parseIsoToTimestamp(dateStr) } ?: System.currentTimeMillis()
                    )
                    list.add(0, newLog)
                    if (list.size > limit) {
                        list.removeAt(list.size - 1)
                    }
                    emit(list.toList())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun observeSmsPreviews(childId: String, limit: Int): Flow<List<SmsPreview>> = flow {
        // 1. Initial fetch
        val dtos = supabaseClient.postgrest.from("sms_previews")
            .select {
                filter {
                    eq("child_id", childId)
                }
                order(column = "created_at", order = Order.DESCENDING)
                limit(count = limit.toLong())
            }.decodeList<SmsPreviewDto>()

        val list = dtos.map {
            SmsPreview(
                id = it.id ?: 0L,
                childId = it.childId,
                phoneNumber = it.phoneNumber,
                contactName = it.contactName,
                messageBody = it.messageBody,
                timestamp = it.createdAt?.let { dateStr -> parseIsoToTimestamp(dateStr) } ?: System.currentTimeMillis()
            )
        }.toMutableList()

        emit(list.toList())

        // 2. Real-time stream
        val channel = supabaseClient.realtime.channel("sms_previews_stream_$childId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "sms_previews"
        }
        channel.subscribe()

        changeFlow.collect { action ->
            if (action is PostgresAction.Insert) {
                val dto = Json.decodeFromJsonElement<SmsPreviewDto>(action.record)
                if (dto.childId == childId) {
                    val newSms = SmsPreview(
                        id = dto.id ?: 0L,
                        childId = dto.childId,
                        phoneNumber = dto.phoneNumber,
                        contactName = dto.contactName,
                        messageBody = dto.messageBody,
                        timestamp = dto.createdAt?.let { dateStr -> parseIsoToTimestamp(dateStr) } ?: System.currentTimeMillis()
                    )
                    list.add(0, newSms)
                    if (list.size > limit) {
                        list.removeAt(list.size - 1)
                    }
                    emit(list.toList())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun observeContacts(childId: String): Flow<List<ContactProfile>> = flow {
        // Contacts are fully replaced on child-app sync, so we query sorted alphabetically by name
        val dtos = supabaseClient.postgrest.from("child_contacts")
            .select {
                filter {
                    eq("child_id", childId)
                }
                order(column = "name", order = Order.ASCENDING)
            }.decodeList<ContactProfileDto>()

        val list = dtos.map {
            ContactProfile(
                id = it.id ?: 0L,
                childId = it.childId,
                name = it.name,
                phoneNumber = it.phoneNumber
            )
        }

        emit(list)

        // Contacts don't sync constantly, but we can hook a change listener for updates
        val channel = supabaseClient.realtime.channel("child_contacts_stream_$childId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "child_contacts"
        }
        channel.subscribe()

        changeFlow.collect {
            // Re-query and re-emit everything to ensure alphabetized sorting stays completely intact
            val freshDtos = supabaseClient.postgrest.from("child_contacts")
                .select {
                    filter {
                        eq("child_id", childId)
                    }
                    order(column = "name", order = Order.ASCENDING)
                }.decodeList<ContactProfileDto>()

            emit(freshDtos.map {
                ContactProfile(
                    id = it.id ?: 0L,
                    childId = it.childId,
                    name = it.name,
                    phoneNumber = it.phoneNumber
                )
            })
        }
    }.flowOn(Dispatchers.IO)

    // Helper to convert ISO-8601 UTC date string into standard Epoch Milliseconds
    private fun parseIsoToTimestamp(isoString: String): Long {
        return try {
            val formatted = isoString.replace("Z", "+00:00")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(formatted)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
