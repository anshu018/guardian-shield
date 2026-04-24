package com.guardianshield.parent.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "parent_cache")

@Singleton
class ParentDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val LOGGED_IN = booleanPreferencesKey("logged_in")
        val FAMILY_ID = stringPreferencesKey("family_id")
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        context.dataStore.edit { it[LOGGED_IN] = loggedIn }
    }

    suspend fun isLoggedIn(): Boolean {
        return context.dataStore.data.map { it[LOGGED_IN] ?: false }.first()
    }

    suspend fun saveFamilyId(id: String) {
        context.dataStore.edit { it[FAMILY_ID] = id }
    }

    suspend fun getFamilyId(): String? {
        return context.dataStore.data.map { it[FAMILY_ID] }.first()
    }
}
