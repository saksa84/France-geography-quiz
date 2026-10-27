package io.github.saksa84.francemapquiz.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Un seul fichier de préférences pour toute l'application (règle DataStore : une instance par fichier). */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "france_map_quiz")

/** Réglages modifiables dans l'écran Paramètres. */
data class Settings(
    /** Son de bonne ou de mauvaise réponse dans les quiz. */
    val answerSounds: Boolean = true,
)

/**
 * Sauvegarde locale (sur le téléphone, sans compte ni serveur) de la progression et des réglages.
 * La progression est stockée en JSON dans une seule préférence : simple, et facile à faire évoluer
 * grâce à son numéro de version.
 */
class ProgressStore(private val context: Context) {

    val progress: Flow<Progress> = context.dataStore.data.map { Progress.fromJson(it[PROGRESS]) }

    val settings: Flow<Settings> = context.dataStore.data.map {
        Settings(answerSounds = it[ANSWER_SOUNDS] ?: true)
    }

    suspend fun save(progress: Progress) {
        context.dataStore.edit { it[PROGRESS] = progress.toJson() }
    }

    suspend fun reset() {
        context.dataStore.edit { it.remove(PROGRESS) }
    }

    suspend fun setAnswerSounds(enabled: Boolean) {
        context.dataStore.edit { it[ANSWER_SOUNDS] = enabled }
    }

    private companion object {
        val PROGRESS = stringPreferencesKey("progress_v1")
        val ANSWER_SOUNDS = booleanPreferencesKey("answer_sounds")
    }
}
