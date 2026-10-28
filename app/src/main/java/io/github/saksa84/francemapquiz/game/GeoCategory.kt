package io.github.saksa84.francemapquiz.game

import io.github.saksa84.francemapquiz.data.Category

/**
 * Ce qu'on apprend : premier choix du menu. Chaque catégorie a ses lieux, ses niveaux et sa progression.
 * @param configKey clé de ses niveaux dans `assets/game/levels.json` (« categories »)
 */
enum class GeoCategory(val category: Category, val configKey: String) {
    DEPARTMENTS(Category.DEPARTMENT, "departments"),

    /** Facultatif : les régions servent surtout à regrouper les départements. */
    REGIONS(Category.REGION, "regions"),

    /** Version 2 : fleuves et rivières, des lignes sur la carte. */
    RIVERS(Category.RIVER, "rivers"),

    /** Version 2 : massifs (zones) et sommets (points). */
    MOUNTAINS(Category.MOUNTAIN, "mountains"),
    ;

    /** Clé de progression : une par combinaison catégorie × mode × type de question. */
    fun progressKey(mode: String, question: QuestionType): String = "${name}_${mode}_${question.name}"
}
