package io.github.saksa84.francemapquiz.game

import io.github.saksa84.francemapquiz.data.GeoCatalog
import io.github.saksa84.francemapquiz.data.GeoEntity
import java.text.Normalizer
import java.util.Locale

/**
 * Met une réponse tapée sous une forme comparable : minuscules, sans accents (Ardèche → ardeche),
 * sans espaces, apostrophes ni tirets (Côtes-d'Armor → cotesdarmor). Seules les lettres et les chiffres restent.
 */
object AnswerNormalizer {
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(NOT_ALPHANUMERIC, "")
}

/**
 * Numéros de département : « 1 » et « 01 » désignent l'Ain, « 2a » vaut « 2A ».
 * « 20 », l'ancien numéro de la Corse, n'est pas accepté (il ne désigne plus aucun département).
 */
object CodeNormalizer {
    private val SPACES = Regex("\\s+")

    fun normalize(text: String): String {
        val compact = text.replace(SPACES, "").uppercase(Locale.ROOT)
        return if (compact.length == 1 && compact[0].isDigit()) "0$compact" else compact
    }

    /** Ordre officiel des numéros : 2A et 2B à la place de l'ancien 20, puis l'outre-mer (971…). */
    fun order(code: String): Double = when (code) {
        "2A" -> 20.1
        "2B" -> 20.2
        else -> code.toDoubleOrNull() ?: Double.MAX_VALUE
    }
}

/**
 * Valide une réponse tapée (mode Saisir). Noms et chefs-lieux : nom officiel ou alias déclarés dans les
 * données, après normalisation ; aucune autre faute n'est tolérée (règle reprise de China Map Quiz).
 * Numéros : voir [CodeNormalizer].
 */
class AnswerChecker(private val catalog: GeoCatalog) {

    private val accepted: Map<String, Set<String>> = catalog.entities.associate { entity ->
        entity.id to forms(entity)
    }

    fun isCorrect(targetId: String, typed: String, question: QuestionType): Boolean {
        val target = catalog[targetId] ?: return false
        return when (question) {
            QuestionType.NAME -> matches(target.id, typed)
            QuestionType.CAPITAL -> target.capitalId?.let { matches(it, typed) } == true
            QuestionType.CODE -> target.code != null && CodeNormalizer.normalize(typed) == target.code
        }
    }

    private fun matches(id: String, typed: String): Boolean {
        val normalized = AnswerNormalizer.normalize(typed)
        return normalized.isNotEmpty() && normalized in accepted[id].orEmpty()
    }

    companion object {
        /** Formes normalisées acceptées pour un lieu : son nom et ses alias. */
        fun forms(entity: GeoEntity): Set<String> =
            (listOf(entity.name) + entity.aliases).map(AnswerNormalizer::normalize).filter { it.isNotEmpty() }.toSet()
    }
}
