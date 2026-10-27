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
}
