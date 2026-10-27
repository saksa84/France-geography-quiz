package io.github.saksa84.francemapquiz.game

import io.github.saksa84.francemapquiz.data.AssetGeoRepository
import io.github.saksa84.francemapquiz.data.Category
import io.github.saksa84.francemapquiz.data.GeoCatalogLoader
import io.github.saksa84.francemapquiz.data.LoadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Niveaux construits à partir du vrai `levels.json` et des vraies données. */
class QuizLevelsTest {

    private val catalog = (GeoCatalogLoader.load(
        *AssetGeoRepository.CATALOG_FILES.map { File("src/main/assets/geo/$it").readText() }.toTypedArray(),
    ) as LoadResult.Success).catalog
    private val config = LevelsConfig.parse(File("src/main/assets/game/levels.json").readText())
    private val allIds = catalog.entities.map { it.id }.toSet()
    private val levels = QuizLevels.buildAll(config, catalog, allIds)
    private val departments = levels.getValue(GeoCategory.DEPARTMENTS)

    private fun section(name: String) = departments.levels.filter { it.section == name }
    private fun level(id: String) = departments.levels.single { it.id == id }

    @Test
    fun questionTypes_perCategory() {
        assertEquals(listOf(QuestionType.NAME, QuestionType.CODE, QuestionType.CAPITAL), departments.questionTypes)
        assertEquals(listOf(QuestionType.NAME, QuestionType.CAPITAL), levels.getValue(GeoCategory.REGIONS).questionTypes)
    }

    @Test
    fun everyConfiguredLevelHasPlaces_andEveryRegionIdExists() {
        val configured = config.categories.values.flatMap { it.sections }.flatMap { it.levels }
        configured.flatMap { it.regionIds.orEmpty() }.forEach { assertTrue(it, catalog[it] != null) }
        val built = levels.values.flatMap { it.levels }.map { it.id }.toSet()
        assertEquals("Niveaux vides : ${configured.map { it.id } - built}", configured.map { it.id }.toSet(), built)
    }

    @Test
    fun regionLevels_coverEachDepartmentOnce() {
        val ids = section("Par région").flatMap { it.targetIds }
        assertEquals(101, ids.size)
        assertEquals(101, ids.toSet().size)
        assertEquals(13, level("reg-occ").targetIds.size)
        assertEquals(5, level("outre-mer").targetIds.size)
    }

    @Test
    fun zones_coverMetropolitanFranceOnce() {
        val ids = section("Par grande zone").flatMap { it.targetIds }
        assertEquals(96, ids.size)
        assertEquals(96, ids.toSet().size)
    }

    @Test
    fun numberRanges_coverAllDepartmentsOnce_inOfficialOrder() {
        val ids = section("Par numéro").flatMap { it.targetIds }
        assertEquals(101, ids.size)
        assertEquals(101, ids.toSet().size)
        // Tranches de 10 ; 2A et 2B à la place de l'ancien 20, avec les 10 à 19.
        assertEquals(9, level("num-01-09").targetIds.size)
        val second = level("num-10-2b").targetIds
        assertEquals(12, second.size)
        assertTrue("DEP-2A" in second && "DEP-2B" in second && "DEP-21" !in second && "DEP-09" !in second)
        assertEquals(5, level("num-91-95").targetIds.size)
    }

    @Test
    fun wholeCountry_levels() {
        assertEquals(96, level("metropole").targetIds.size)
        assertEquals(101, level("france").targetIds.size)
        val regions = levels.getValue(GeoCategory.REGIONS).levels
        assertEquals(13, regions.single { it.id == "regions-metropole" }.targetIds.size)
        assertEquals(18, regions.single { it.id == "regions-france" }.targetIds.size)
        assertTrue(regions.flatMap { it.targetIds }.all { catalog[it]?.category == Category.REGION })
    }

    @Test
    fun placesThatAreNotPlayable_areLeftOut_andEmptyLevelsDropped() {
        val withoutCorsica = allIds - setOf("DEP-2A", "DEP-2B")
        val built = QuizLevels.buildAll(config, catalog, withoutCorsica).getValue(GeoCategory.DEPARTMENTS).levels
        assertTrue(built.none { it.id == "reg-cor" })
        assertEquals(10, built.single { it.id == "num-10-2b" }.targetIds.size)
    }

    @Test
    fun progressKeys_separateCategoryModeAndQuestion() {
        assertEquals("DEPARTMENTS_FIND_CODE", GeoCategory.DEPARTMENTS.progressKey("FIND", QuestionType.CODE))
        assertTrue(GeoCategory.DEPARTMENTS.progressKey("FIND", QuestionType.NAME) != GeoCategory.DEPARTMENTS.progressKey("FIND", QuestionType.CAPITAL))
    }

    @Test
    fun questionValues() {
        val cantal = catalog["DEP-15"]!!
        assertEquals("Cantal", QuestionType.NAME.valueOf(cantal, catalog))
        assertEquals("15", QuestionType.CODE.valueOf(cantal, catalog))
        assertEquals("Aurillac", QuestionType.CAPITAL.valueOf(cantal, catalog))
    }
}
