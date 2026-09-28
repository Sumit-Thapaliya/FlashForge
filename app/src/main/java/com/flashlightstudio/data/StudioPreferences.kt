package com.flashlightstudio.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "flashlight_studio")
data class SavedPattern(val name: String, val segments: List<PatternSegment>)

class StudioPreferences(private val context: Context) {
    private val patternsKey = stringPreferencesKey("patterns_v1")
    private val darkKey = booleanPreferencesKey("dark_theme")
    private val hapticsKey = booleanPreferencesKey("haptics")
    val darkTheme: Flow<Boolean> = context.store.data.map { it[darkKey] ?: true }
    val haptics: Flow<Boolean> = context.store.data.map { it[hapticsKey] ?: true }
    val patterns: Flow<List<SavedPattern>> = context.store.data.map { prefs -> decode(prefs[patternsKey].orEmpty()) }
    suspend fun setDarkTheme(value: Boolean) { context.store.edit { it[darkKey] = value } }
    suspend fun setHaptics(value: Boolean) { context.store.edit { it[hapticsKey] = value } }
    suspend fun savePattern(pattern: SavedPattern) {
        context.store.edit { prefs ->
            val current = decode(prefs[patternsKey].orEmpty()).filterNot { it.name == pattern.name } + pattern
            prefs[patternsKey] = encode(current)
        }
    }
    suspend fun deletePattern(name: String) {
        context.store.edit { prefs -> prefs[patternsKey] = encode(decode(prefs[patternsKey].orEmpty()).filterNot { it.name == name }) }
    }
    private fun encode(items: List<SavedPattern>): String = items.joinToString("#") { p ->
        "${p.name.replace("#", " ").replace("~", " ")}~" + p.segments.joinToString(",") { "${if (it.on) 1 else 0}:${it.durationMs.coerceIn(50, 5000)}" }
    }
    private fun decode(raw: String): List<SavedPattern> = raw.split("#").mapNotNull { entry ->
        val parts = entry.split("~", limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) null else {
            val segments = parts[1].split(",").mapNotNull { token ->
                val pair = token.split(":")
                if (pair.size != 2) null else runCatching { PatternSegment(pair[0] == "1", pair[1].toLong().coerceIn(50, 5000)) }.getOrNull()
            }
            SavedPattern(parts[0], segments)
        }
    }
}
