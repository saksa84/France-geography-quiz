package io.github.saksa84.francemapquiz.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Grande famille de lieux. Chaque catégorie correspond à un fichier de données. */
enum class Category {
    REGION,
    DEPARTMENT,

    /** Chef-lieu (préfecture) d'un département : un point sur la carte. */
    PREFECTURE,

    /** Fleuve ou rivière : une ligne sur la carte. */
    RIVER,

    /** Massif (zone) et sommet (point). */
    MOUNTAIN,
}

/** Forme de la géométrie à dessiner et à toucher sur la carte. */
enum class GeometryKind {
    POLYGON,

    /** Chefs-lieux ; plus tard les sommets. */
    POINT,

    /** Cours d'eau : tronçons ouverts. */
    LINE,

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
    /** Faux quand le parent est facultatif (un fleuve n'en a pas, un affluent si). */
    val parentRequired: Boolean = parentCategories.isNotEmpty(),
) {
    /** Région ; sa géométrie est l'union de ses départements. */
    @SerialName("region") REGION(Category.REGION, GeometryKind.COMPOSITE),

    @SerialName("department") DEPARTMENT(Category.DEPARTMENT, GeometryKind.POLYGON, setOf(Category.REGION)),

    /** Chef-lieu de département (préfecture). Parent : son département. */
    @SerialName("prefecture") PREFECTURE(Category.PREFECTURE, GeometryKind.POINT, setOf(Category.DEPARTMENT)),

    /** Cours d'eau. Parent : le cours d'eau dans lequel il se jette ; aucun pour un fleuve. */
    @SerialName("river") RIVER(Category.RIVER, GeometryKind.LINE, setOf(Category.RIVER), parentRequired = false),

    /** Massif au sens de la loi Montagne : une zone, union des communes classées dans le massif. */
    @SerialName("massif") MASSIF(Category.MOUNTAIN, GeometryKind.POLYGON),

    /** Sommet : un point. Parent : son massif. */
    @SerialName("peak") PEAK(Category.MOUNTAIN, GeometryKind.POINT, setOf(Category.MOUNTAIN)),
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
 * Un lieu : région, département, chef-lieu, cours d'eau, massif ou sommet.
 *
 * La géométrie est stockée à part (`map.geometry.json`), indexée par [id] : on peut ainsi charger
 * les noms sans les tracés et remplacer la carte sans toucher aux noms.
 */
@Serializable
data class GeoEntity(
    /** Identifiant stable, indépendant du nom : `DEP-15`, `DEP-2A`, `REG-OCC`, `CL-15`, `RV-LOIRE`, `MT-ALPES`, `PK-MONTBLANC`. */
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
    /**
     * Départements concernés : traversés par un cours d'eau (au moins 5 km), couverts par un massif
     * (au moins une commune), ou celui où se trouve un sommet.
     */
    val departmentIds: List<String> = emptyList(),
    /** Sommets : altitude en mètres (Wikidata). */
    val altitudeM: Int? = null,
    /** Sommets : élément Wikidata et révision utilisée (`Q583@2557648539`). */
    val wikidata: String? = null,
    /** Cours d'eau : codes hydrographiques BD TOPAGE (CdOH) des objets qui forment son tracé. */
    val sandreCodes: List<String> = emptyList(),
    val sourceId: String,
) {
    val category: Category get() = type.category

    /** « Cantal (15) » ; le nom seul pour une région ou un chef-lieu. */
    val displayName: String get() = if (type == FeatureType.DEPARTMENT && code != null) "$name ($code)" else name
}
