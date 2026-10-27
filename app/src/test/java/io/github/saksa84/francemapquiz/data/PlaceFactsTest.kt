package io.github.saksa84.francemapquiz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fiches « En savoir plus » : règles de validation, puis vraies données livrées. */
class PlaceFactsTest {

    private fun asset(path: String) = File("src/main/assets/$path").readText()

    private val catalog = (GeoCatalogLoader.load(*AssetGeoRepository.CATALOG_FILES.map { asset("geo/$it") }.toTypedArray()) as LoadResult.Success).catalog

    private fun file(vararg facts: String) = """{"schemaVersion":1,"origin":"test","facts":[${facts.joinToString(",")}]}"""

    private fun errorsOf(text: String): List<FactsError> =
        when (val result = PlaceFactsLoader.load(text, catalog)) {
            is FactsResult.Invalid -> result.errors
            else -> throw AssertionError("Erreurs attendues, obtenu : $result")
        }

    @Test
    fun validFacts_areIndexedById() {
        val result = PlaceFactsLoader.load(file("""{"id":"DEP-15","nameOrigin":"Des monts du Cantal."}"""), catalog)
        val facts = (result as FactsResult.Success).facts
        assertEquals("Des monts du Cantal.", facts["DEP-15"]!!.nameOrigin)
        assertEquals(false, facts["DEP-15"]!!.reviewed)
    }

    @Test
    fun unknownOrDuplicateIds_areRejected() {
        val cantal = """{"id":"DEP-15","history":"x"}"""
        assertEquals(listOf(FactsError.DuplicateId("DEP-15")), errorsOf(file(cantal, cantal)))
        assertEquals(listOf(FactsError.UnknownId("DEP-20")), errorsOf(file("""{"id":"DEP-20","history":"?"}""")))
    }

    @Test
    fun emptyOrTooLongFacts_areRejected() {
        assertEquals(listOf(FactsError.Empty("DEP-15")), errorsOf(file("""{"id":"DEP-15"}""")))
        val long = "a".repeat(PlaceFactsLoader.MAX_TEXT_LENGTH + 1)
        assertEquals(listOf(FactsError.TooLong("DEP-15", long)), errorsOf(file("""{"id":"DEP-15","legend":"$long"}""")))
        val fourAnecdotes = """{"id":"DEP-15","anecdotes":["a","b","c","d"]}"""
        assertEquals(listOf(FactsError.TooLong("DEP-15", "4 anecdotes")), errorsOf(file(fourAnecdotes)))
    }

    /**
     * Règle de vérification (CLAUDE.md, section 7) : une fiche livrée a été vérifiée phrase par phrase,
     * avec ses sources dans docs/verification-fiches.md. Une fiche non vérifiée ne doit pas partir.
     */
    @Test
    fun shippedFacts_areValid_andAllReviewed() {
        val result = PlaceFactsLoader.load(asset("geo/facts.json"), catalog)
        assertTrue("facts.json doit être valide : $result", result is FactsResult.Success)
        val unreviewed = (result as FactsResult.Success).facts.values.filter { !it.reviewed }.map { it.id }
        assertEquals("Fiches non vérifiées : $unreviewed", emptyList<String>(), unreviewed)
    }

    /** Chaque fiche livrée a une entrée dans le registre des sources. */
    @Test
    fun shippedFacts_areListedInTheVerificationRegister() {
        val facts = (PlaceFactsLoader.load(asset("geo/facts.json"), catalog) as FactsResult.Success).facts
        val register = File("../docs/verification-fiches.md").readText()
        val missing = facts.keys.filter { it !in register }
        assertEquals("Fiches absentes de docs/verification-fiches.md : $missing", emptyList<String>(), missing)
    }
}
