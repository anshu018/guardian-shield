package com.guardianshield.child.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guardianshield.child.domain.models.ChildLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
}
