package io.github.saksa84.francemapquiz.data

import io.github.saksa84.francemapquiz.game.AnswerChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Vérifie les vrais fichiers de données livrés dans l'application (app/src/main/assets/geo).
 * Les tests unitaires s'exécutent depuis le dossier du module `app`, d'où le chemin relatif.
 */
class RealAssetsTest {

    private fun asset(name: String): String = File("src/main/assets/geo/$name").readText()

    private val catalog: GeoCatalog by lazy {
        when (val result = GeoCatalogLoader.load(*AssetGeoRepository.CATALOG_FILES.map(::asset).toTypedArray())) {
            is LoadResult.Success -> result.catalog
            else -> throw AssertionError("Les données livrées doivent être valides : $result")
        }
    }

    private val departments by lazy { catalog.inCategory(Category.DEPARTMENT) }
    private val regions by lazy { catalog.inCategory(Category.REGION) }

    @Test
    fun has101Departments_96InMetropolitanFranceAnd5Overseas() {
        assertEquals(101, departments.size)
        assertEquals(96, departments.count { !it.overseas })
        assertEquals(listOf("971", "972", "973", "974", "976"), departments.filter { it.overseas }.map { it.code })
    }

    @Test
    fun has13MetropolitanRegionsAnd5Overseas() {
        assertEquals(13, regions.count { !it.overseas })
        assertEquals(5, regions.count { it.overseas })
        // Chaque région d'outre-mer n'a qu'un département.
        regions.filter { it.overseas }.forEach { assertEquals(it.id, 1, catalog.departmentsOf(it.id).size) }
    }

    @Test
    fun codesAreExactlyTheOfficialList() {
        val expected = (1..19).map { "%02d".format(it) } + listOf("2A", "2B") + (21..95).map { "%02d".format(it) } +
            listOf("971", "972", "973", "974", "976")
        assertEquals(expected.sorted(), departments.map { it.code }.sortedBy { it })
    }

    @Test
    fun idsFollowTheCodes() {
        departments.forEach { d ->
            assertEquals("DEP-${d.code}", d.id)
            assertEquals("CL-${d.code}", d.capitalId)
        }
        assertEquals(101, catalog.inCategory(Category.PREFECTURE).size)
    }

    @Test
    fun departmentCountsPerRegion() {
        val expected = mapOf(
            "REG-ARA" to 12, "REG-BFC" to 8, "REG-BRE" to 4, "REG-CVL" to 6, "REG-COR" to 2, "REG-GES" to 10,
            "REG-HDF" to 5, "REG-IDF" to 8, "REG-NOR" to 5, "REG-NAQ" to 12, "REG-OCC" to 13, "REG-PDL" to 5, "REG-PAC" to 6,
        )
        expected.forEach { (id, count) -> assertEquals(id, count, catalog.departmentsOf(id).size) }
    }

    /** Cas tranchés dans docs/plan-initial.md, vérifiés sur le Code officiel géographique 2026. */
    @Test
    fun documentedSpecialCases() {
        fun capital(code: String) = catalog.capitalOf(catalog["DEP-$code"]!!)!!.name
        assertEquals("REG-COR", catalog.regionOf("DEP-2A")!!.id)
        assertEquals("REG-COR", catalog.regionOf("DEP-2B")!!.id)
        assertEquals("Ajaccio", capital("2A"))
        assertEquals("Bastia", capital("2B"))
        assertEquals("Strasbourg", capital("67"))
        assertEquals("Colmar", capital("68"))
        assertEquals("Lyon", capital("69")) // un seul département 69, Métropole de Lyon comprise
        assertEquals("Paris", capital("75"))
        assertEquals("Basse-Terre", capital("971")) // et non Pointe-à-Pitre
        assertEquals("Pontoise", capital("95")) // chef-lieu officiel ; la préfecture est à Cergy
        assertEquals("Toulon", capital("83"))
        assertEquals("Le Puy-en-Velay", capital("43"))
        assertEquals("Évry-Courcouronnes", capital("91"))
    }

    /** Notes administratives : seulement les cas tranchés, chacune avec sa source sur Légifrance. */
    @Test
    fun administrativeNotesHaveTheirSource() {
        val withNote = departments.filter { it.note != null }.mapNotNull { it.code }
        assertEquals(listOf("2A", "2B", "67", "68", "69", "75", "972", "973", "976").sorted(), withNote.sorted())
        departments.filter { it.note != null }.forEach { assertTrue(it.id, it.noteSource!!.contains("legifrance.gouv.fr")) }
    }

    @Test
    fun regionCapitalsAreCapitalsOfOneOfTheirDepartments() {
        assertEquals("Lyon", catalog.capitalOf(catalog["REG-ARA"]!!)!!.name)
        assertEquals("Toulouse", catalog.capitalOf(catalog["REG-OCC"]!!)!!.name)
        assertEquals("Ajaccio", catalog.capitalOf(catalog["REG-COR"]!!)!!.name)
        regions.forEach { region ->
            val capital = catalog.capitalOf(region)!!
            assertEquals(region.id, catalog.regionOf(capital.parentId!!)!!.id)
        }
    }

    /**
     * Dans une même famille (départements, régions, chefs-lieux), aucun nom ni alias ne doit donner la
     * même forme normalisée qu'un autre lieu : sinon le mode Saisir ne saurait pas les distinguer.
     * Entre familles, c'est permis : Paris est un département et un chef-lieu, Mayotte une région et un département.
     */
    @Test
    fun normalizedNamesAreUniqueWithinEachFamily() {
        for (category in Category.entries) {
            val owners = mutableMapOf<String, String>()
            for (entity in catalog.inCategory(category)) {
                for (form in AnswerChecker.forms(entity)) {
                    val previous = owners.put(form, entity.id)
                    assertTrue("« $form » : ${entity.id} et $previous", previous == null || previous == entity.id)
                }
            }
        }
    }

    @Test
    fun saintAbbreviationsAreListedInTheData() {
        assertTrue("St-Brieuc" in catalog["CL-22"]!!.aliases)
        assertTrue("St-Étienne" in catalog["CL-42"]!!.aliases)
        assertTrue("Seine-St-Denis" in catalog["DEP-93"]!!.aliases)
        // Alias décidés le 27 octobre 2026 (docs/plan-initial.md).
        assertTrue("Évry" in catalog["CL-91"]!!.aliases)
        assertTrue("PACA" in catalog["REG-PAC"]!!.aliases)
    }

    @Test
    fun namesHaveTheirAccentsAndHyphens() {
        assertEquals("Ardèche", catalog["DEP-07"]!!.name)
        assertEquals("Côtes-d'Armor", catalog["DEP-22"]!!.name)
        assertEquals("Puy-de-Dôme", catalog["DEP-63"]!!.name)
        assertEquals("Pyrénées-Atlantiques", catalog["DEP-64"]!!.name)
    }

    @Test
    fun areasAreRealistic() {
        // Superficies officielles de l'INSEE (base du comparateur de territoires).
        assertEquals(105, catalog["DEP-75"]!!.areaKm2)
        assertEquals(9_976, catalog["DEP-33"]!!.areaKm2) // et non 10 358 km², estuaire compris
        assertTrue(catalog["DEP-973"]!!.areaKm2!! in 83_000..85_000)
        // Mayotte est absente des fichiers INSEE : rien plutôt qu'une autre source.
        assertEquals(null, catalog["DEP-976"]!!.areaKm2)
        departments.filter { it.code != "976" }.forEach { assertTrue(it.id, (it.areaKm2 ?: 0) > 0) }
    }

    @Test
    fun populationsAreTheInseeReferencePopulations2023() {
        assertEquals(2_103_778L, catalog["DEP-75"]!!.population)
        assertEquals(2023, catalog["DEP-75"]!!.populationYear)
        assertEquals(null, catalog["DEP-976"]!!.population)
        departments.filter { it.code != "976" }.forEach { assertTrue(it.id, (it.population ?: 0) > 0) }
        // La population d'une région est la somme de celles de ses départements.
        regions.filter { it.population != null }.forEach { region ->
            assertEquals(region.id, region.population, catalog.departmentsOf(region.id).sumOf { it.population ?: 0 })
        }
    }
}
