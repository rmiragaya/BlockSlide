package com.rokoc.blockslide.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.progressDataStore by preferencesDataStore(name = "block_slide_progress")

class ProgressRepository(
    private val context: Context,
) {
    private val currentLevelKey = intPreferencesKey("current_level_index")
    private val solvedLevelsKey = stringPreferencesKey("solved_level_ids")

    val currentLevelIndex: Flow<Int> =
        context.progressDataStore.data.map { preferences -> preferences[currentLevelKey] ?: 0 }

    val solvedLevelIds: Flow<Set<String>> =
        context.progressDataStore.data.map { preferences ->
            preferences[solvedLevelsKey]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
        }

    suspend fun setCurrentLevel(index: Int) {
        context.progressDataStore.edit { preferences ->
            preferences[currentLevelKey] = index
        }
    }

    suspend fun markSolved(levelId: String) {
        context.progressDataStore.edit { preferences ->
            val current = preferences[solvedLevelsKey]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableSet()
                ?: mutableSetOf()
            current += levelId
            preferences[solvedLevelsKey] = current.sorted().joinToString(",")
        }
    }
}
