package io.github.saksa84.francemapquiz.game

import io.github.saksa84.francemapquiz.data.GeoCatalog
import io.github.saksa84.francemapquiz.data.GeoEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ce qu'on demande sur un lieu. Un département a trois choses à retenir : son nom, son numéro
 * et son chef-lieu. Dans Trouver et Éliminer, la consigne montre cette valeur (« Trouver : 15 »)
 * et l'on touche le département ; dans Saisir, le département est coloré et l'on tape cette valeur.
 *
 * C'est un paramètre du niveau (`levels.json`), pas un mode à part.
 */
@Serializable
enum class QuestionType {
    @SerialName("name") NAME,
    @SerialName("code") CODE,
    @SerialName("capital") CAPITAL,
    ;

    /** La valeur demandée pour ce lieu (« Cantal », « 15 », « Aurillac »), ou null s'il n'en a pas. */
    fun valueOf(entity: GeoEntity, catalog: GeoCatalog): String? = when (this) {
        NAME -> entity.name
        CODE -> entity.code
        CAPITAL -> catalog.capitalOf(entity)?.name
    }
}
