package io.github.saksa84.francemapquiz.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Grande famille de lieux. Chaque catégorie correspond à un fichier de données ; les fleuves et
 * les montagnes viendront avec la version 2 (voir CLAUDE.md, section 4).
 */
enum class Category {
    REGION,
    DEPARTMENT,

    /** Chef-lieu (préfecture) d'un département : un point sur la carte. */
    PREFECTURE,
}

/** Forme de la géométrie à dessiner et à toucher sur la carte. */
enum class GeometryKind {
    POLYGON,

    /** Chefs-lieux ; plus tard les sommets. */
    POINT,

    /** Pas de tracé propre : union des départements (régions). */
    COMPOSITE,
}

/**
 * Type précis d'un lieu. Il fixe sa catégorie, sa forme et les parents autorisés
 * (un département appartient à une région, un chef-lieu à un département).
 */
@Serializable
enum class FeatureType(
    val category: Category,
    val geometry: GeometryKind,
    /** Catégories autorisées pour le parent. Vide : le lieu n'a jamais de parent. */
    val parentCategories: Set<Category> = emptySet(),
) {
    /** Région ; sa géométrie est l'union de ses départements. */
    @SerialName("region") REGION(Category.REGION, GeometryKind.COMPOSITE),

    @SerialName("department") DEPARTMENT(Category.DEPARTMENT, GeometryKind.POLYGON, setOf(Category.REGION)),

    /** Chef-lieu de département (préfecture). Parent : son département. */
    @SerialName("prefecture") PREFECTURE(Category.PREFECTURE, GeometryKind.POINT, setOf(Category.DEPARTMENT)),
    ;

    val parentRequired: Boolean get() = parentCategories.isNotEmpty()
}

/** Provenance d'un ensemble de données, référencée par [GeoEntity.sourceId]. */
@Serializable
data class DataSource(
    val id: String,
    val name: String,
    val license: String,
    val url: String? = null,
    val version: String? = null,
)

/**
 * Un lieu : région, département ou chef-lieu.
 *
 * La géométrie est stockée à part (`map.geometry.json`), indexée par [id] : on peut ainsi charger
 * les noms sans les tracés et remplacer la carte sans toucher aux noms.
 */
@Serializable
data class GeoEntity(
    /** Identifiant stable, indépendant du nom : `DEP-15`, `DEP-2A`, `REG-OCC`, `CL-15`. */
    val id: String,
    val type: FeatureType,
    /** Nom officiel en français, avec accents, traits d'union et article (« Le Puy-en-Velay »). */
    val name: String,
    /** Autres graphies acceptées dans le mode Saisir (« St-Brieuc », « Réunion »). */
    val aliases: List<String> = emptyList(),
    /**
     * Numéro officiel (code INSEE) : chaîne de caractères, car la Corse a 2A et 2B.
     * Départements : 01 à 95, 2A, 2B, 971 à 976. Régions : code INSEE à deux chiffres.
     */
    val code: String? = null,
    /** Département ou région : son chef-lieu (entité de type « prefecture »). */
    val capitalId: String? = null,
    /** Parent hiérarchique ; les catégories autorisées dépendent de [type]. */
    val parentId: String? = null,
    /** Département ou région d'outre-mer, dessiné dans un cartouche. */
    val overseas: Boolean = false,
    /** Départements : superficie officielle en km² (INSEE). Absente pour Mayotte (voir docs/data-sources.md). */
    val areaKm2: Int? = null,
    /** Population municipale (INSEE, populations de référence) et son année ; absente pour Mayotte. */
    val population: Long? = null,
    val populationYear: Int? = null,
    /** Note administrative (Collectivité de Corse, Métropole de Lyon…), vérifiée sur [noteSource]. */
    val note: String? = null,
    /** Adresse de la source de [note] (Légifrance). */
    val noteSource: String? = null,
    /** Chefs-lieux : code INSEE de la commune. */
    val inseeCode: String? = null,
    val sourceId: String,
) {
    val category: Category get() = type.category

    /** « Cantal (15) » ; le nom seul pour une région ou un chef-lieu. */
    val displayName: String get() = if (type == FeatureType.DEPARTMENT && code != null) "$name ($code)" else name
}
