package io.github.saksa84.francemapquiz.progress

import io.github.saksa84.francemapquiz.game.QuizSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Meilleur résultat obtenu sur un niveau d'un mode. */
@Serializable
data class LevelBest(
    /** Lieux trouvés du premier coup. */
    val correct: Int,
    val total: Int,
    val timeMs: Long,
    /** Erreurs (compte surtout en mode Éliminer, où elles sont illimitées). */
    val errors: Int = 0,
) {
    val perfect: Boolean get() = total > 0 && correct == total
    val fraction: Float get() = if (total == 0) 0f else correct.toFloat() / total

    /** Plus de bonnes réponses ; à égalité, moins d'erreurs ; puis plus rapide. */
    fun isBetterThan(other: LevelBest?): Boolean = when {
        other == null -> true
        correct != other.correct -> correct > other.correct
        errors != other.errors -> errors < other.errors
        else -> timeMs < other.timeMs
    }
}

/**
 * Progression sauvegardée sur le téléphone : meilleurs résultats par niveau, mode et type de question,
 * et nombre de fois où chaque lieu a été manqué (pour réviser les erreurs fréquentes).
 * Objet immuable : chaque partie produit une nouvelle progression.
 */
@Serializable
data class Progress(
    val schemaVersion: Int = 1,
    /** Clé : [key] (mode + niveau). */
    val best: Map<String, LevelBest> = emptyMap(),
    /** Clé d'erreur ([missKey] : type de question + lieu) → nombre de parties où elle a été commise. */
    val missCounts: Map<String, Int> = emptyMap(),
    val gamesPlayed: Int = 0,
) {
    fun bestFor(mode: String, levelId: String): LevelBest? = best[key(mode, levelId)]

    /**
     * Ajoute une partie terminée.
     * @param countsForBest false pour une partie « rejouer les erreurs » : elle ne porte que sur
     *   une partie du niveau et ne doit pas devenir son meilleur score
     * @param question type de question de la partie : manquer le numéro du Cantal n'est pas manquer son nom
     */
    fun record(mode: String, levelId: String, summary: QuizSummary, question: String, countsForBest: Boolean = true): RecordOutcome {
        val result = LevelBest(summary.correct, summary.total, summary.durationMs, summary.mistakes.size)
        val k = key(mode, levelId)
        val newRecord = countsForBest && summary.total > 0 && result.isBetterThan(best[k])
        val misses = missCounts.toMutableMap()
        summary.missedTargetIds.forEach { id -> missKey(question, id).let { misses[it] = (misses[it] ?: 0) + 1 } }
        val updated = copy(
            best = if (newRecord) best + (k to result) else best,
            missCounts = misses,
            gamesPlayed = gamesPlayed + 1,
        )
        return RecordOutcome(updated, newRecord)
    }

    /** Part des lieux trouvés du premier coup, cumulée sur les meilleurs résultats d'un mode (0 à 1). */
    fun completion(mode: String, levels: List<Pair<String, Int>>): Float {
        val total = levels.sumOf { it.second }
        if (total == 0) return 0f
        val done = levels.sumOf { (id, size) -> (bestFor(mode, id)?.correct ?: 0).coerceAtMost(size) }
        return done.toFloat() / total
    }

    /** Erreurs les plus fréquentes ([missKey]), de la plus fréquente à la moins fréquente. */
    fun mostMissed(limit: Int): List<Pair<String, Int>> =
        missCounts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun key(mode: String, levelId: String) = "$mode/$levelId"

        /** « CODE:DEP-15 » : le numéro du Cantal a été manqué. */
        fun missKey(question: String, id: String) = "$question:$id"

        /** Inverse de [missKey] : (type de question, identifiant du lieu), ou null pour une clé illisible. */
        fun parseMissKey(key: String): Pair<String, String>? =
            key.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }

        /** Lecture tolérante : des données illisibles ne doivent jamais bloquer l'application. */
        fun fromJson(text: String?): Progress =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: Progress()
    }
}

data class RecordOutcome(val progress: Progress, val newRecord: Boolean)
