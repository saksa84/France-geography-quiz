package io.github.saksa84.francemapquiz.data

import kotlinx.serialization.Serializable

/** Contenu brut d'un fichier de données, tel qu'il est lu dans le JSON. */
@Serializable
data class GeoCatalogFile(
    val schemaVersion: Int,
    val sources: List<DataSource>,
    val entities: List<GeoEntity>,
)

/**
 * Ensemble de lieux validé, issu d'un ou plusieurs fichiers, avec des accès rapides.
 * On ne le construit qu'à partir de données sans erreur (voir [GeoCatalogLoader]).
 */
class GeoCatalog(
    val sources: List<DataSource>,
    val entities: List<GeoEntity>,
) {
    private val byId: Map<String, GeoEntity> = entities.associateBy { it.id }
    private val byParent: Map<String?, List<GeoEntity>> = entities.groupBy { it.parentId }

    operator fun get(id: String): GeoEntity? = byId[id]

    fun children(parentId: String): List<GeoEntity> = byParent[parentId].orEmpty()

    fun inCategory(category: Category): List<GeoEntity> = entities.filter { it.category == category }

    /** Départements d'une région (pour la colorier ou poser une question). */
    fun departmentsOf(regionId: String): List<GeoEntity> = children(regionId).filter { it.type == FeatureType.DEPARTMENT }

    /** Région d'un département ; null pour un autre lieu. */
    fun regionOf(departmentId: String): GeoEntity? =
        byId[departmentId]?.takeIf { it.type == FeatureType.DEPARTMENT }?.parentId?.let { byId[it] }

    /** Chef-lieu d'un département ou d'une région. */
    fun capitalOf(entity: GeoEntity): GeoEntity? = entity.capitalId?.let { byId[it] }

    /** Cours d'eau qui se jettent dans celui-ci. */
    fun tributariesOf(riverId: String): List<GeoEntity> = children(riverId).filter { it.type == FeatureType.RIVER }

    /** Fleuve au bout de la chaîne des confluences (la Loire pour la Creuse) ; le lieu lui-même s'il n'a pas de parent. */
    fun mouthOf(entity: GeoEntity): GeoEntity {
        var current = entity
        repeat(entities.size) { current = current.parentId?.let { byId[it] } ?: return current }
        return current
    }

    /** Parcs nationaux dont le cœur est (en partie) dans un département. */
    fun parksOf(departmentId: String): List<GeoEntity> =
        entities.filter { it.type == FeatureType.PARK && departmentId in it.departmentIds }

    /** Sommets d'un massif, dans l'ordre des données (le plus haut d'abord). */
    fun peaksOf(massifId: String): List<GeoEntity> = children(massifId).filter { it.type == FeatureType.PEAK }

    /** Massifs qui couvrent au moins une commune d'un département. */
    fun massifsOf(departmentId: String): List<GeoEntity> =
        entities.filter { it.type == FeatureType.MASSIF && departmentId in it.departmentIds }

    /** Cours d'eau qui traversent un département, dans l'ordre des données (fleuves d'abord). */
    fun riversThrough(departmentId: String): List<GeoEntity> =
        entities.filter { it.type == FeatureType.RIVER && departmentId in it.departmentIds }

    /**
     * Départements dont le nom contient celui du cours d'eau comme mot entier : la Loire se retrouve dans
     * Haute-Loire et Maine-et-Loire, mais le Loir n'est pas dans Loire, ni l'Ill dans Ille-et-Vilaine.
     */
    fun departmentsNamedAfter(river: GeoEntity): List<GeoEntity> = entities.filter { department ->
        department.type == FeatureType.DEPARTMENT && department.name.split('-', ' ', '\'', '’').any { it == river.name }
    }
}
