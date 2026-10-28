package io.github.saksa84.francemapquiz.data

import io.github.saksa84.francemapquiz.game.AnswerChecker
import io.github.saksa84.francemapquiz.game.GeoCategory
import io.github.saksa84.francemapquiz.game.LevelsConfig
import io.github.saksa84.francemapquiz.game.QuestionType
import io.github.saksa84.francemapquiz.game.QuizLevels
import io.github.saksa84.francemapquiz.map.HitTester
import io.github.saksa84.francemapquiz.map.MapGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Version 3 sur les vraies données : parcs nationaux (`parcs.json`, tools/build_parks.py) et collectivités
 * d'outre-mer (`outre-mer.json`, tools/build_overseas.py).
 */
class RealParksAndOverseasTest {

    private fun asset(name: String): String = File("src/main/assets/geo/$name").readText()

    private val catalog = (GeoCatalogLoader.load(*AssetGeoRepository.CATALOG_FILES.map(::asset).toTypedArray()) as LoadResult.Success).catalog
    private val geometry = AssetGeoRepository.GEOMETRY_FILES.map { MapGeometry.parse(asset(it)) }.reduce(MapGeometry::plus)
    private val parks = catalog.entities.filter { it.type == FeatureType.PARK }
    private val collectivities = catalog.entities.filter { it.type == FeatureType.COLLECTIVITY }
    private val levels by lazy {
        val config = LevelsConfig.parse(File("src/main/assets/game/levels.json").readText())
        QuizLevels.buildAll(config, catalog, catalog.entities.map { it.id }.toSet())
    }

    @Test
    fun elevenNationalParks_withCreationDateAndCoreArea() {
        assertEquals(11, parks.size)
        assertEquals(8, parks.count { !it.overseas })
        val vanoise = catalog["PN-VANOISE"]!!
        assertEquals("1963-07-06", vanoise.established) // le premier parc national
        assertEquals(534, vanoise.areaKm2)
        assertEquals(listOf("DEP-73"), vanoise.departmentIds)
        assertEquals("2019-11-06", catalog["PN-FORETS"]!!.established) // le plus récent
        assertEquals(17_913, catalog["PN-GUYANE"]!!.areaKm2) // le plus grand
        assertTrue(parks.all { it.note!!.startsWith("Décret") && it.noteSource!!.contains("data.gouv.fr") })
        assertEquals(listOf("Parc national des Cévennes"), catalog.parksOf("DEP-48").map { it.name })
        assertTrue(catalog.parksOf("DEP-75").isEmpty())
    }

    /** Le nom d'un parc est écrit sur sa partie terrestre, même quand le cœur marin est bien plus grand (Calanques). */
    @Test
    fun parkLabels_onLand_andEachParkSelectable() {
        val departments = geometry.shapes.filter { catalog[it.id]?.type == FeatureType.DEPARTMENT }
        for (park in parks) {
            val shape = geometry[park.id]!!
            assertTrue(park.id, shape.contains(shape.label.x, shape.label.y))
            assertTrue(park.id, departments.any { it.frame == shape.frame && it.contains(shape.label.x, shape.label.y) })
        }
        val tester = HitTester(parks.map { geometry[it.id]!! })
        for (park in parks) assertEquals(park.id, park.id, tester.hit(geometry[park.id]!!.label))
    }

    @Test
    fun typedParkNames() {
        val checker = AnswerChecker(catalog)
        assertTrue(checker.isCorrect("PN-VANOISE", "Vanoise", QuestionType.NAME))
        assertTrue(checker.isCorrect("PN-ECRINS", "parc national des ecrins", QuestionType.NAME))
        assertTrue(checker.isCorrect("PN-GUYANE", "Parc amazonien", QuestionType.NAME))
    }

    @Test
    fun sixOverseasCollectivities_withCodeAndCapital() {
        assertEquals(listOf("975", "977", "978", "986", "987", "988"), collectivities.map { it.code })
        fun capital(code: String) = catalog.capitalOf(catalog["COM-$code"]!!)!!.name
        assertEquals("Saint-Pierre", capital("975"))
        assertEquals("Gustavia", capital("977"))
        assertEquals("Marigot", capital("978"))
        assertEquals("Mata-Utu", capital("986"))
        assertEquals("Papeete", capital("987"))
        assertEquals("Nouméa", capital("988"))
        assertTrue(collectivities.all { it.overseas && it.note != null && it.noteSource!!.contains("conseil-constitutionnel.fr") })
        assertTrue(catalog["COM-988"]!!.note!!.contains("titre XIII"))
        assertEquals("Saint-Martin (978)", catalog["COM-978"]!!.displayName)
    }

    @Test
    fun overseasCategory_mixesDepartmentsAndCollectivities() {
        val overseas = levels.getValue(GeoCategory.OVERSEAS)
        assertEquals(listOf(QuestionType.NAME, QuestionType.CODE, QuestionType.CAPITAL), overseas.questionTypes)
        fun level(id: String) = overseas.levels.single { it.id == id }.targetIds
        assertEquals(listOf("DEP-971", "DEP-972", "DEP-973", "DEP-974", "DEP-976"), level("om-departements"))
        assertEquals(6, level("om-collectivites").size)
        assertEquals(11, level("om-tout").size)
        // La catégorie Départements ne change pas.
        assertEquals(101, levels.getValue(GeoCategory.DEPARTMENTS).levels.single { it.id == "france" }.targetIds.size)
        val checker = AnswerChecker(catalog)
        assertTrue(checker.isCorrect("COM-987", "987", QuestionType.CODE))
        assertTrue(checker.isCorrect("COM-986", "Mata Utu", QuestionType.CAPITAL))
        assertTrue(checker.isCorrect("COM-975", "saint pierre et miquelon", QuestionType.NAME))
    }

    /** Une seconde rangée de 6 cartouches, sous ceux des départements d'outre-mer ; chaque chef-lieu dans sa collectivité. */
    @Test
    fun collectivityInsets() {
        val ids = listOf("975", "977", "978", "986", "987", "988")
        assertEquals(listOf("metro", "971", "972", "973", "974", "976") + ids, geometry.frames.map { it.id })
        val dromBottom = geometry.frames.filter { it.id.startsWith("97") && it.id.length == 3 && it.id !in ids }.maxOf { it.bounds.maxY }
        for (code in ids) {
            val frame = geometry.frame(code)!!
            assertTrue(code, frame.bounds.minY > dromBottom)
            val shape = geometry["COM-$code"]!!
            assertEquals(code, shape.frame)
            val capital = geometry["CL-$code"]!!
            assertTrue(code, shape.contains(capital.label.x, capital.label.y))
        }
        // Sans les collectivités, la carte garde sa vue d'ensemble habituelle (métropole et départements d'outre-mer).
        val usual = geometry.boundsOf(setOf("metro", "971", "972", "973", "974", "976"))
        assertTrue(usual.maxY < geometry.bounds.maxY)
    }
}
