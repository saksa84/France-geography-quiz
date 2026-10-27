package io.github.saksa84.francemapquiz.game

import io.github.saksa84.francemapquiz.data.FeatureType
import io.github.saksa84.francemapquiz.data.GeoCatalog
import io.github.saksa84.francemapquiz.data.GeoEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Contenu de `assets/game/levels.json`. Les niveaux se règlent là, pas dans le code. */
@Serializable
data class LevelsConfig(
    val schemaVersion: Int,
    /** Niveaux de chaque catégorie, par clé de catégorie (voir [GeoCategory.configKey]). */
    val categories: Map<String, CategoryConfig>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): LevelsConfig = json.decodeFromString<LevelsConfig>(text).also {
            require(it.schemaVersion == 1) { "Version de niveaux non prise en charge : ${it.schemaVersion}" }
        }
    }
}

@Serializable
data class CategoryConfig(
    /** Types de question proposés dans cette catégorie (pas de numéro pour les régions, par exemple). */
    val questionTypes: List<QuestionType>,
    val sections: List<SectionConfig>,
)

/** Groupe de niveaux affiché sous un même titre (« Par région », « Par numéro »…). */
@Serializable
data class SectionConfig(val name: String, val levels: List<LevelConfig>)

/**
 * Un niveau, décrit par des critères plutôt que par une liste en dur ; les critères présents se cumulent.
 * @param regionIds départements de ces régions (ou ces régions elles-mêmes, dans la catégorie Régions)
 * @param codeFrom premier numéro compris, dans l'ordre officiel (2A et 2B entre 19 et 21)
 * @param codeTo dernier numéro compris
 * @param overseas false : métropole seulement ; true : outre-mer seulement ; absent : les deux
 * @param memberIds liste explicite de lieux
 * @param basinIds cours d'eau qui aboutissent à l'un de ces fleuves (le fleuve compris) : le bassin de la Loire
 * @param mouthsOnly true : seulement les fleuves, qui se jettent dans la mer ; false : seulement les affluents
 */
@Serializable
data class LevelConfig(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val regionIds: List<String>? = null,
    val codeFrom: String? = null,
    val codeTo: String? = null,
    val overseas: Boolean? = null,
    val memberIds: List<String>? = null,
    val basinIds: List<String>? = null,
    val mouthsOnly: Boolean? = null,
)

/** Un niveau jouable, avec la liste exacte des lieux à trouver. */
data class QuizLevel(
    val id: String,
    val name: String,
    /** Titre de la section où il est rangé. */
    val section: String,
    val targetIds: List<String>,
    val subtitle: String? = null,
)

/** Niveaux et types de question d'une catégorie. */
data class CategoryLevels(val questionTypes: List<QuestionType>, val levels: List<QuizLevel>)

object QuizLevels {

    /**
     * Niveaux de toutes les catégories ; un niveau sans aucun lieu jouable est écarté.
     *
     * @param playableIds lieux réellement jouables (présents dans les données ET dessinés sur la carte) :
     *   aucune question ne peut viser un lieu absent.
     */
    fun buildAll(config: LevelsConfig, catalog: GeoCatalog, playableIds: Set<String>): Map<GeoCategory, CategoryLevels> =
        GeoCategory.entries.associateWith { category ->
            val categoryConfig = config.categories[category.configKey] ?: return@associateWith CategoryLevels(emptyList(), emptyList())
            val candidates = catalog.inCategory(category.category).filter { it.id in playableIds }
            val levels = categoryConfig.sections.flatMap { section ->
                section.levels.mapNotNull { level ->
                    val ids = candidates.filter { matches(it, level, catalog) }.map { it.id }
                    ids.takeIf { it.isNotEmpty() }?.let { QuizLevel(level.id, level.name, section.name, it, level.subtitle) }
                }
            }
            CategoryLevels(categoryConfig.questionTypes, levels)
        }

    private fun matches(entity: GeoEntity, level: LevelConfig, catalog: GeoCatalog): Boolean {
        val regionId = if (entity.type == FeatureType.REGION) entity.id else entity.parentId
        val order = entity.code?.let(CodeNormalizer::order)
        return (level.regionIds == null || regionId in level.regionIds) &&
            (level.memberIds == null || entity.id in level.memberIds) &&
            (level.overseas == null || entity.overseas == level.overseas) &&
            (level.basinIds == null || catalog.mouthOf(entity).id in level.basinIds) &&
            (level.mouthsOnly == null || (entity.parentId == null) == level.mouthsOnly) &&
            (level.codeFrom == null || (order != null && order >= CodeNormalizer.order(level.codeFrom))) &&
            (level.codeTo == null || (order != null && order <= CodeNormalizer.order(level.codeTo)))
    }
}
