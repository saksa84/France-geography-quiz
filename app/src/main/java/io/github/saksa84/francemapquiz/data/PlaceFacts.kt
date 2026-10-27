package io.github.saksa84.francemapquiz.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fiche « En savoir plus » d'un lieu, rattachée par son identifiant (fichier `facts.json`).
 * Contenu non géographique, séparé des noms et des tracés. Toutes les rubriques sont facultatives.
 *
 * Règle (CLAUDE.md, section 7) : chaque phrase a une source citable, notée dans
 * docs/verification-fiches.md ; ce qui est introuvable est retiré, pas remplacé.
 */
@Serializable
data class PlaceFacts(
    val id: String,
    /** Origine du nom : beaucoup de départements portent le nom d'un cours d'eau ou d'un relief. */
    val nameOrigin: String? = null,
    val history: String? = null,
    /** Légende attachée au lieu, présentée comme telle : ce n'est pas un fait historique. */
    val legend: String? = null,
    /** Faits insolites. */
    val anecdotes: List<String> = emptyList(),
    /** Vrai quand chaque phrase a été vérifiée avec une source citable (registre : docs/verification-fiches.md). */
    val reviewed: Boolean = false,
) {
    val texts: List<String> get() = listOfNotNull(nameOrigin, history, legend) + anecdotes
}

@Serializable
data class PlaceFactsFile(
    val schemaVersion: Int,
    /** Origine du contenu (rédaction, sources consultées). */
    val origin: String,
    val facts: List<PlaceFacts>,
)

sealed interface FactsError {
    data class UnsupportedSchema(val version: Int) : FactsError
    data class DuplicateId(val id: String) : FactsError
    /** Fiche d'un lieu absent des données géographiques. */
    data class UnknownId(val id: String) : FactsError
    data class Empty(val id: String) : FactsError
    /** Texte trop long pour rester une fiche courte, ou trop d'anecdotes. */
    data class TooLong(val id: String, val text: String) : FactsError
}

sealed interface FactsResult {
    data class Success(val facts: Map<String, PlaceFacts>) : FactsResult
    data class Malformed(val message: String) : FactsResult
    data class Invalid(val errors: List<FactsError>) : FactsResult
}

/** Lit et valide `facts.json` par rapport au catalogue déjà chargé. Ne dépend pas d'Android. */
object PlaceFactsLoader {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_TEXT_LENGTH = 300
    const val MAX_ANECDOTES = 3

    private val json = Json { ignoreUnknownKeys = true }

    fun load(text: String, catalog: GeoCatalog): FactsResult {
        val file = try {
            json.decodeFromString<PlaceFactsFile>(text)
        } catch (e: SerializationException) {
            return FactsResult.Malformed(e.message ?: "JSON invalide")
        } catch (e: IllegalArgumentException) {
            return FactsResult.Malformed(e.message ?: "JSON invalide")
        }
        val errors = validate(file, catalog)
        return if (errors.isEmpty()) FactsResult.Success(file.facts.associateBy { it.id }) else FactsResult.Invalid(errors)
    }

    fun validate(file: PlaceFactsFile, catalog: GeoCatalog): List<FactsError> {
        val errors = mutableListOf<FactsError>()
        if (file.schemaVersion != SUPPORTED_SCHEMA_VERSION) errors += FactsError.UnsupportedSchema(file.schemaVersion)
        val seen = mutableSetOf<String>()
        for (facts in file.facts) {
            if (!seen.add(facts.id)) errors += FactsError.DuplicateId(facts.id)
            if (catalog[facts.id] == null) errors += FactsError.UnknownId(facts.id)
            if (facts.texts.isEmpty()) errors += FactsError.Empty(facts.id)
            facts.texts.filter { it.isBlank() || it.length > MAX_TEXT_LENGTH }.forEach { errors += FactsError.TooLong(facts.id, it) }
            if (facts.anecdotes.size > MAX_ANECDOTES) errors += FactsError.TooLong(facts.id, "${facts.anecdotes.size} anecdotes")
        }
        return errors
    }
}
