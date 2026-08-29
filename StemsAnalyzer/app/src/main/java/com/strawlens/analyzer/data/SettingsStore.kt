package com.strawlens.analyzer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Persists the user's own Gemini API key locally on the device (DataStore /
 * SharedPreferences-equivalent). The key never leaves the device except in
 * direct calls to Google's Generative Language API.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val LANGUAGE = stringPreferencesKey("language") // "en" or "ar"
        val MODEL = stringPreferencesKey("model_name")
    }

    val apiKeyFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.API_KEY] ?: "" }

    val languageFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }

    val modelFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.MODEL] ?: DEFAULT_MODEL }

    suspend fun getApiKey(): String = apiKeyFlow.first()
    suspend fun getLanguage(): String = languageFlow.first()
    suspend fun getModel(): String = modelFlow.first()

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[Keys.API_KEY] = key.trim() }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { it[Keys.MODEL] = model.trim() }
    }

    companion object {
        // A current, widely available multimodal Gemini model. Changeable in Settings
        // in case Google renames/retires a model version after this app is built.
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
