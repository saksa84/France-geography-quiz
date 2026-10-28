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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Montagnes livrées (`montagnes.json`, `montagnes.geometry.json`, produits par tools/build_mountains.py) :
 * massifs de la loi Montagne (ANCT) et sommets (Wikidata).
 */
class RealMountainsTest {

    private fun asset(name: String): String = File("src/main/assets/geo/$name").readText()

    private val catalog = (GeoCatalogLoader.load(*AssetGeoRepository.CATALOG_FILES.map(::asset).toTypedArray()) as LoadResult.Success).catalog
    private val geometry = AssetGeoRepository.GEOMETRY_FILES.map { MapGeometry.parse(asset(it)) }.reduce(MapGeometry::plus)
    private val massifs = catalog.entities.filter { it.type == FeatureType.MASSIF }
    private val peaks = catalog.entities.filter { it.type == FeatureType.PEAK }

    @Test
    fun nineMassifs_sixInMetropolitanFrance_and30Peaks() {
        assertEquals(
            listOf("Alpes", "Pyrénées", "Massif central", "Jura", "Vosges", "Corse", "Guadeloupe", "Martinique", "Hauts de la Réunion"),
            massifs.map { it.name },
        )
        assertEquals(6, massifs.count { !it.overseas })
        assertEquals(30, peaks.size)
        assertTrue(peaks.all { it.id.startsWith("PK-") && it.wikidata != null && catalog[it.parentId!!]?.type == FeatureType.MASSIF })
    }

    @Test
    fun altitudes_fromWikidata() {
        fun altitude(id: String) = catalog[id]!!.altitudeM
        assertEquals(4806, altitude("PK-MONTBLANC")) // 4 805,59 m, mesure de 2023 (valeur « préférée »)
        assertEquals(3299, altitude("PK-VIGNEMALE"))
        assertEquals(1885, altitude("PK-SANCY"))
        assertEquals(2706, altitude("PK-CINTO"))
        assertEquals(3070, altitude("PK-PITONNEIGES"))
        // Le plus haut sommet de chaque massif de métropole est bien celui qu'on attend.
        val highest = massifs.filter { !it.overseas }.associate { it.name to catalog.peaksOf(it.id).maxByOrNull { p -> p.altitudeM!! }!!.name }
        assertEquals("Mont Blanc", highest["Alpes"])
        assertEquals("Vignemale", highest["Pyrénées"])
        assertEquals("Puy de Sancy", highest["Massif central"])
        assertEquals("Crêt de la Neige", highest["Jura"])
        assertEquals("Grand Ballon", highest["Vosges"])
        assertEquals("Monte Cinto", highest["Corse"])
    }

    @Test
    fun massifsAndDepartments() {
        assertEquals(listOf("Alpes"), catalog.massifsOf("DEP-74").map { it.name })
        assertEquals(listOf("Corse"), catalog.massifsOf("DEP-2A").map { it.name })
        assertTrue(catalog.massifsOf("DEP-75").isEmpty())
        assertTrue(catalog.massifsOf("DEP-29").isEmpty())
        assertTrue("DEP-15" in catalog["MT-MASSIF-CENTRAL"]!!.departmentIds)
        assertEquals(listOf("DEP-74"), catalog["PK-MONTBLANC"]!!.departmentIds)
        assertEquals(listOf("DEP-63"), catalog["PK-SANCY"]!!.departmentIds)
    }

    /** Un sommet tombe dans son massif, et le point d'étiquette d'un massif dans sa zone (leçon de China Map Quiz). */
    @Test
    fun peaksLieInTheirMassif_andMassifLabelsInsideTheirZone() {
        for (peak in peaks) {
            val point = geometry[peak.id]!!
            assertEquals(peak.id, ShapeKind.POINT, point.kind)
            val massif = geometry[peak.parentId!!]!!
            assertTrue(peak.id, massif.contains(point.label.x, point.label.y))
        }
        for (massif in massifs) {
            val shape = geometry[massif.id]!!
            assertEquals(massif.id, ShapeKind.POLYGON, shape.kind)
            assertTrue(massif.id, shape.contains(shape.label.x, shape.label.y))
        }
    }

    /** Toucher un sommet le sélectionne, même quand deux sommets sont voisins (Puy Mary et Plomb du Cantal). */
    @Test
    fun eachPeakIsSelectable() {
        val tester = HitTester(peaks.map { geometry[it.id]!! })
        for (peak in peaks) assertEquals(peak.id, peak.id, tester.hit(geometry[peak.id]!!.label, tolerance = 20f))
    }

    @Test
    fun typedNames() {
        val checker = AnswerChecker(catalog)
        assertTrue(checker.isCorrect("PK-MONTBLANC", "mont blanc", QuestionType.NAME))
        assertTrue(checker.isCorrect("PK-CANIGOU", "Pic du Canigou", QuestionType.NAME))
        assertTrue(checker.isCorrect("PK-MEIJE", "Meije", QuestionType.NAME))
        assertTrue(checker.isCorrect("MT-MASSIF-CENTRAL", "massif central", QuestionType.NAME))
        assertTrue(!checker.isCorrect("PK-PUYDEDOME", "Puy de Sancy", QuestionType.NAME))
    }

    @Test
    fun mountainLevels() {
        val config = LevelsConfig.parse(File("src/main/assets/game/levels.json").readText())
        val levels = QuizLevels.buildAll(config, catalog, catalog.entities.map { it.id }.toSet()).getValue(GeoCategory.MOUNTAINS)
        fun level(id: String) = levels.levels.single { it.id == id }.targetIds
        assertEquals(6, level("massifs-metropole").size)
        assertEquals(9, level("massifs").size)
        assertEquals(30, level("sommets").size)
        val byMassif = levels.levels.filter { it.section == "Sommets par massif" }.flatMap { it.targetIds }
        assertEquals(30, byMassif.size)
        assertEquals(30, byMassif.toSet().size)
        assertTrue(level("sommets-alpes").all { catalog[it]!!.parentId == "MT-ALPES" })
    }
}
