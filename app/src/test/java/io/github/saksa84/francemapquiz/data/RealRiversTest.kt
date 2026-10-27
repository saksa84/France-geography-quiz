package io.github.saksa84.francemapquiz.data

import io.github.saksa84.francemapquiz.game.AnswerChecker
import io.github.saksa84.francemapquiz.game.GeoCategory
import io.github.saksa84.francemapquiz.game.LevelsConfig
import io.github.saksa84.francemapquiz.game.QuestionType
import io.github.saksa84.francemapquiz.game.QuizLevels
import io.github.saksa84.francemapquiz.map.HitTester
import io.github.saksa84.francemapquiz.map.MapGeometry
import io.github.saksa84.francemapquiz.map.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cours d'eau livrés (`cours-eau.json`, `cours-eau.geometry.json`, produits par tools/build_rivers.py
 * à partir de BD TOPAGE 2025) : liste, rattachements, départements traversés, tracés et niveaux.
 */
class RealRiversTest {

    private fun asset(name: String): String = File("src/main/assets/geo/$name").readText()

    private val catalog = (GeoCatalogLoader.load(*AssetGeoRepository.CATALOG_FILES.map(::asset).toTypedArray()) as LoadResult.Success).catalog
    private val geometry = AssetGeoRepository.GEOMETRY_FILES.map { MapGeometry.parse(asset(it)) }.reduce(MapGeometry::plus)
    private val rivers = catalog.inCategory(Category.RIVER)

    private fun river(id: String) = catalog[id]!!
    private fun names(entities: List<GeoEntity>) = entities.map { it.name }

    @Test
    fun has47Rivers_16FlowingIntoTheSea() {
        assertEquals(47, rivers.size)
        assertEquals(16, rivers.count { it.parentId == null })
        assertTrue(rivers.all { it.id.startsWith("RV-") && it.sourceId == "sandre-bd-topage-2025" && it.sandreCodes.isNotEmpty() })
        // La Somme et l'Escaut sont formés de deux objets BD TOPAGE (cours amont et partie canalisée).
        assertEquals(2, river("RV-SOMME").sandreCodes.size)
        assertEquals(2, river("RV-ESCAUT").sandreCodes.size)
    }

    /** Rattachements vérifiés sur Wikipédia (révisions listées dans tools/build_rivers.py). */
    @Test
    fun tributariesFlowIntoTheRightRiver() {
        assertEquals("RV-VIENNE", river("RV-CREUSE").parentId)
        assertEquals("RV-SARTHE", river("RV-LOIR").parentId)
        assertEquals("RV-OISE", river("RV-AISNE").parentId)
        assertEquals("RV-SAONE", river("RV-DOUBS").parentId)
        assertEquals("RV-TARN", river("RV-AVEYRON").parentId)
        assertEquals("RV-MOSELLE", river("RV-MEURTHE").parentId)
        // La Dordogne est un fleuve : elle forme l'estuaire de la Gironde avec la Garonne.
        assertNull(river("RV-DORDOGNE").parentId)
        assertEquals(listOf("Mayenne", "Sarthe"), names(catalog.tributariesOf("RV-MAINE")))
        assertEquals("RV-LOIRE", catalog.mouthOf(river("RV-LOIR")).id)
        assertEquals("RV-RHIN", catalog.mouthOf(river("RV-MEURTHE")).id)
        assertEquals("RV-SEINE", catalog.mouthOf(river("RV-SEINE")).id)
    }

    @Test
    fun departmentsCrossed() {
        // Wikipédia : la Somme « traverse les deux départements de l'Aisne et de la Somme ».
        assertEquals(listOf("DEP-02", "DEP-80"), river("RV-SOMME").departmentIds)
        assertEquals(listOf("DEP-67", "DEP-68"), river("RV-RHIN").departmentIds)
        assertEquals(listOf("DEP-26"), river("RV-DROME").departmentIds)
        assertTrue(river("RV-LOIRE").departmentIds.containsAll(listOf("DEP-07", "DEP-43", "DEP-42", "DEP-45", "DEP-44")))
        assertTrue(river("RV-SEINE").departmentIds.containsAll(listOf("DEP-21", "DEP-75", "DEP-76")))
        assertTrue(rivers.all { it.departmentIds.isNotEmpty() })
        // Fiche d'un département : les cours d'eau qui le traversent.
        assertTrue(names(catalog.riversThrough("DEP-49")).containsAll(listOf("Loire", "Maine", "Mayenne", "Sarthe", "Loir")))
    }

    @Test
    fun departmentsNamedAfterARiver_matchWholeWordsOnly() {
        assertEquals(
            listOf("Haute-Loire", "Loire", "Loire-Atlantique", "Maine-et-Loire", "Indre-et-Loire", "Saône-et-Loire").sorted(),
            names(catalog.departmentsNamedAfter(river("RV-LOIRE"))).sorted(),
        )
        assertEquals(listOf("Eure-et-Loir", "Loir-et-Cher").sorted(), names(catalog.departmentsNamedAfter(river("RV-LOIR"))).sorted())
        assertEquals(listOf("Val-d'Oise", "Oise").sorted(), names(catalog.departmentsNamedAfter(river("RV-OISE"))).sorted())
        assertEquals(emptyList<String>(), names(catalog.departmentsNamedAfter(river("RV-ILL")))) // pas Ille-et-Vilaine
        assertEquals(listOf("Ille-et-Vilaine"), names(catalog.departmentsNamedAfter(river("RV-VILAINE"))))
    }

    @Test
    fun typedNames_acceptTheArticle() {
        val checker = AnswerChecker(catalog)
        assertTrue(checker.isCorrect("RV-LOIRE", "la loire", QuestionType.NAME))
        assertTrue(checker.isCorrect("RV-ISERE", "l'Isere", QuestionType.NAME))
        assertTrue(checker.isCorrect("RV-RHONE", "Rhone", QuestionType.NAME))
        assertTrue(!checker.isCorrect("RV-LOIR", "Loire", QuestionType.NAME))
    }

    @Test
    fun everyRiverHasALine_insideMetropolitanFrance() {
        val metro = geometry.frame("metro")!!.bounds
        for (r in rivers) {
            val shape = geometry[r.id]!!
            assertEquals(r.id, ShapeKind.LINE, shape.kind)
            assertTrue(r.id, shape.rings.isNotEmpty() && shape.rings.all { it.size >= 4 })
            val b = shape.bounds
            assertTrue(r.id, b.minX >= metro.minX && b.maxX <= metro.maxX && b.minY >= metro.minY && b.maxY <= metro.maxY)
        }
    }

    /** Toucher le milieu d'un cours d'eau (son point d'étiquette) le sélectionne, même la Maine à Angers. */
    @Test
    fun eachRiverIsSelectable_atItsLabel() {
        val tester = HitTester(rivers.map { geometry[it.id]!! })
        for (r in rivers) {
            val shape = geometry[r.id]!!
            assertEquals(r.id, r.id, tester.hit(shape.label, tolerance = 4f))
        }
    }

    @Test
    fun riverLevels_coverEachRiverOnce() {
        val config = LevelsConfig.parse(File("src/main/assets/game/levels.json").readText())
        val levels = QuizLevels.buildAll(config, catalog, catalog.entities.map { it.id }.toSet()).getValue(GeoCategory.RIVERS)
        assertEquals(listOf(QuestionType.NAME), levels.questionTypes)
        val basins = levels.levels.filter { it.section == "Par bassin" }.flatMap { it.targetIds }
        assertEquals(47, basins.size)
        assertEquals(47, basins.toSet().size)
        fun level(id: String) = levels.levels.single { it.id == id }.targetIds
        assertEquals(10, level("bassin-loire").size)
        assertTrue("RV-VEZERE" in level("bassin-garonne") && "RV-DORDOGNE" in level("bassin-garonne"))
        assertEquals(16, level("fleuves").size)
        assertEquals(31, level("affluents").size)
        assertEquals(47, level("cours-eau").size)
    }
}
