package io.github.saksa84.francemapquiz.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface LoadResult {
    data class Success(val catalog: GeoCatalog) : LoadResult

    /** Un fichier est illisible (syntaxe, champ obligatoire absent, type inconnu…). */
    data class Malformed(val message: String) : LoadResult

    /** Les fichiers sont lisibles mais incohérents, seuls ou entre eux. */
    data class Invalid(val errors: List<ValidationError>) : LoadResult
}

/**
 * Transforme le texte d'un ou plusieurs fichiers de données (régions, départements, chefs-lieux)
 * en un seul [GeoCatalog] validé. Les fichiers sont validés ensemble, car ils se référencent :
 * un département pointe vers sa région et son chef-lieu.
 *
 * Ne dépend pas d'Android : la lecture des fichiers (assets, ressources de test) se fait ailleurs.
 */
object GeoCatalogLoader {
    private val json = Json {
        // Un champ inconnu ne bloque pas le chargement : on pourra enrichir les données
        // (population…) avant que l'application ne les utilise.
        ignoreUnknownKeys = true
    }

    fun load(vararg texts: String): LoadResult {
        val files = try {
            texts.map { json.decodeFromString<GeoCatalogFile>(it) }
        } catch (e: SerializationException) {
            return LoadResult.Malformed(e.message ?: "JSON invalide")
        } catch (e: IllegalArgumentException) {
            return LoadResult.Malformed(e.message ?: "JSON invalide")
        }

        val errors = GeoCatalogValidator.validate(files)
        if (errors.isNotEmpty()) return LoadResult.Invalid(errors)

        return LoadResult.Success(
            GeoCatalog(
                sources = files.flatMap { it.sources }.distinctBy { it.id },
                entities = files.flatMap { it.entities },
            ),
        )
    }
}
