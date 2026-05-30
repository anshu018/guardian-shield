package com.guardianshield.child.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guardianshield.child.data.remote.dto.ChildLocationDto
import com.guardianshield.child.domain.models.ChildLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "location_cache")

@Singleton
class LocationDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val CHILD_ID = stringPreferencesKey("child_id")
        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LNG = doublePreferencesKey("last_lng")
        val LAST_BATTERY = intPreferencesKey("last_battery")
        val LAST_ACCURACY = floatPreferencesKey("last_accuracy")
        val LAST_TIMESTAMP = longPreferencesKey("last_timestamp")
        val SETUP_COMPLETED = stringPreferencesKey("setup_completed")
        val FAMILY_ID = stringPreferencesKey("family_id")
        val PARENT_PIN = stringPreferencesKey("parent_pin")
        val OFFLINE_CACHE = stringPreferencesKey("offline_cache")
        val BLOCKED_PACKAGES = stringPreferencesKey("blocked_packages")
        val PARENT_PHONE = stringPreferencesKey("parent_phone")
        val CHILD_NAME = stringPreferencesKey("child_name")
        val STATIONARY_START_TIME = longPreferencesKey("stationary_start_time")
        val LAST_CALL_SYNC = longPreferencesKey("last_call_sync")
        val LAST_SMS_SYNC = longPreferencesKey("last_sms_sync")
        val LAST_CONTACTS_SYNC = longPreferencesKey("last_contacts_sync")
    }

    suspend fun saveLastKnownLocation(location: ChildLocation) {
        context.dataStore.edit { prefs ->
            prefs[CHILD_ID] = location.childId
            prefs[LAST_LAT] = location.lat
            prefs[LAST_LNG] = location.lng
            prefs[LAST_BATTERY] = location.battery
            prefs[LAST_ACCURACY] = location.accuracy
            prefs[LAST_TIMESTAMP] = location.timestamp
        }
    }

    suspend fun getLastKnownLocation(): ChildLocation? {
        val prefs = context.dataStore.data.first()
        val lat = prefs[LAST_LAT] ?: return null
        val lng = prefs[LAST_LNG] ?: return null
        return ChildLocation(
            childId = prefs[CHILD_ID] ?: "",
            lat = lat,
            lng = lng,
            battery = prefs[LAST_BATTERY] ?: 0,
            accuracy = prefs[LAST_ACCURACY] ?: 0f,
            timestamp = prefs[LAST_TIMESTAMP] ?: 0L
        )
    }

    suspend fun saveOfflineLocation(dto: ChildLocationDto) {
        context.dataStore.edit { prefs ->
            val currentCacheJson = prefs[OFFLINE_CACHE] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<ChildLocationDto>>(currentCacheJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            currentList.add(dto)
            if (currentList.size > 5) {
                currentList.removeAt(0)
            }
            prefs[OFFLINE_CACHE] = Json.encodeToString(currentList)
        }
    }

    suspend fun getOfflineLocations(): List<ChildLocationDto> {
        val prefs = context.dataStore.data.first()
        val cacheJson = prefs[OFFLINE_CACHE] ?: "[]"
        return try {
            Json.decodeFromString(cacheJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearOfflineLocations() {
        context.dataStore.edit { prefs ->
            prefs[OFFLINE_CACHE] = "[]"
        }
    }

    suspend fun isSetupCompleted(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[SETUP_COMPLETED] == "true"
    }

    suspend fun markSetupCompleted() {
        context.dataStore.edit { it[SETUP_COMPLETED] = "true" }
    }

    suspend fun saveFamilyId(id: String) {
        context.dataStore.edit { it[FAMILY_ID] = id }
    }

    suspend fun getFamilyId(): String? {
        return context.dataStore.data.map { it[FAMILY_ID] }.first()
    }

    suspend fun saveParentPin(pin: String) {
        context.dataStore.edit { it[PARENT_PIN] = pin }
    }

    suspend fun getParentPin(): String? {
        return context.dataStore.data.map { it[PARENT_PIN] }.first()
    }

    suspend fun saveBlockedPackages(packages: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[BLOCKED_PACKAGES] = Json.encodeToString(packages)
        }
    }

    suspend fun getBlockedPackages(): List<String> {
        val prefs = context.dataStore.data.first()
        val json = prefs[BLOCKED_PACKAGES] ?: "[]"
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveParentPhone(phone: String) {
        context.dataStore.edit { it[PARENT_PHONE] = phone }
    }

    suspend fun getParentPhone(): String? {
        return context.dataStore.data.map { it[PARENT_PHONE] }.first()
    }

    suspend fun saveChildName(name: String) {
        context.dataStore.edit { it[CHILD_NAME] = name }
    }

    suspend fun getChildName(): String? {
        return context.dataStore.data.map { it[CHILD_NAME] }.first()
    }

    suspend fun saveStationaryStartTime(time: Long) {
        context.dataStore.edit { it[STATIONARY_START_TIME] = time }
    }

    suspend fun getStationaryStartTime(): Long {
        return context.dataStore.data.map { it[STATIONARY_START_TIME] }.first() ?: 0L
    }

    suspend fun saveLastCallSync(time: Long) {
        context.dataStore.edit { it[LAST_CALL_SYNC] = time }
    }

    suspend fun getLastCallSync(): Long {
        return context.dataStore.data.map { it[LAST_CALL_SYNC] }.first() ?: 0L
    }

    suspend fun saveLastSmsSync(time: Long) {
        context.dataStore.edit { it[LAST_SMS_SYNC] = time }
    }

    suspend fun getLastSmsSync(): Long {
        return context.dataStore.data.map { it[LAST_SMS_SYNC] }.first() ?: 0L
    }

    suspend fun saveLastContactsSync(time: Long) {
        context.dataStore.edit { it[LAST_CONTACTS_SYNC] = time }
    }

    suspend fun getLastContactsSync(): Long {
        return context.dataStore.data.map { it[LAST_CONTACTS_SYNC] }.first() ?: 0L
    }
}
