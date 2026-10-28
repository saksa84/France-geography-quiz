package io.github.saksa84.francemapquiz.game

import io.github.saksa84.francemapquiz.data.Category

/**
 * Ce qu'on apprend : premier choix du menu. Chaque catégorie a ses lieux, ses niveaux et sa progression.
 * @param category famille de lieux principale
 * @param configKey clé de ses niveaux dans `assets/game/levels.json` (« categories »)
 * @param also autres familles réunies dans la même catégorie (les départements d'outre-mer dans « Outre-mer »)
 */
enum class GeoCategory(val category: Category, val configKey: String, val also: Set<Category> = emptySet()) {
    DEPARTMENTS(Category.DEPARTMENT, "departments"),

    /** Facultatif : les régions servent surtout à regrouper les départements. */
    REGIONS(Category.REGION, "regions"),

    /** Version 2 : fleuves et rivières, des lignes sur la carte. */
    RIVERS(Category.RIVER, "rivers"),

    /** Version 2 : massifs (zones) et sommets (points). */
    MOUNTAINS(Category.MOUNTAIN, "mountains"),

    /** Version 3 : les 11 parcs nationaux. */
    PARKS(Category.PARK, "parks"),

    /** Version 3 : départements et collectivités d'outre-mer, avec numéro et chef-lieu. */
    OVERSEAS(Category.COLLECTIVITY, "overseas", also = setOf(Category.DEPARTMENT)),
    ;

    /** Toutes les familles de lieux de la catégorie. */
    val categories: Set<Category> get() = also + category

    /** Clé de progression : une par combinaison catégorie × mode × type de question. */
    fun progressKey(mode: String, question: QuestionType): String = "${name}_${mode}_${question.name}"
}
