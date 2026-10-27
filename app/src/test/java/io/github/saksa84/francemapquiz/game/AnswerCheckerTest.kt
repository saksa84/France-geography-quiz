package io.github.saksa84.francemapquiz.game

import io.github.saksa84.francemapquiz.data.AssetGeoRepository
import io.github.saksa84.francemapquiz.data.GeoCatalogLoader
import io.github.saksa84.francemapquiz.data.LoadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Validation des réponses tapées (mode Saisir), sur les vraies données. */
class AnswerCheckerTest {

    private val catalog = (GeoCatalogLoader.load(
        *AssetGeoRepository.CATALOG_FILES.map { File("src/main/assets/geo/$it").readText() }.toTypedArray(),
    ) as LoadResult.Success).catalog
    private val checker = AnswerChecker(catalog)

    private fun name(id: String, typed: String) = checker.isCorrect(id, typed, QuestionType.NAME)
    private fun code(id: String, typed: String) = checker.isCorrect(id, typed, QuestionType.CODE)
    private fun capital(id: String, typed: String) = checker.isCorrect(id, typed, QuestionType.CAPITAL)

    @Test
    fun normalizer_ignoresCaseAccentsSpacesHyphensAndApostrophes() {
        assertEquals("cotesdarmor", AnswerNormalizer.normalize("Côtes-d'Armor"))
        assertEquals("cotesdarmor", AnswerNormalizer.normalize("cotes d armor"))
        assertEquals("ardeche", AnswerNormalizer.normalize("ARDÈCHE"))
        assertEquals("cotesdarmor", AnswerNormalizer.normalize("Côtes d’Armor")) // apostrophe typographique
    }

    @Test
    fun names_acceptAccentAndPunctuationVariants() {
        assertTrue(name("DEP-07", "Ardeche"))
        assertTrue(name("DEP-07", "ardèche"))
        assertTrue(name("DEP-22", "cotes d armor"))
        assertTrue(name("DEP-63", "puy de dome"))
        assertTrue(name("DEP-90", "territoire-de-belfort"))
    }

    @Test
    fun names_rejectAnyOtherMistake() {
        assertFalse(name("DEP-07", "Ardech"))
        assertFalse(name("DEP-15", "Cantale"))
        assertFalse(name("DEP-15", "Puy-de-Dôme"))
        assertFalse(name("DEP-15", ""))
        assertFalse(name("DEP-15", "   "))
    }

    @Test
    fun abbreviationsListedInTheData_areAccepted() {
        assertTrue(name("DEP-93", "Seine-St-Denis"))
        assertTrue(capital("DEP-22", "St-Brieuc"))
        assertTrue(capital("DEP-22", "st brieuc"))
        assertTrue(capital("DEP-42", "St Etienne"))
        assertTrue(name("DEP-974", "Réunion"))
        // Une abréviation non listée n'est pas acceptée.
        assertFalse(capital("DEP-22", "S-Brieuc"))
    }

    @Test
    fun codes_acceptLeadingZeroOrNot() {
        assertTrue(code("DEP-01", "01"))
        assertTrue(code("DEP-01", "1"))
        assertTrue(code("DEP-09", "9"))
        assertTrue(code("DEP-15", " 15 "))
        assertTrue(code("DEP-974", "974"))
        assertFalse(code("DEP-01", "001"))
        assertFalse(code("DEP-15", "16"))
    }

    @Test
    fun corsicanCodes_areCaseInsensitive_and20IsRejected() {
        assertTrue(code("DEP-2A", "2A"))
        assertTrue(code("DEP-2A", "2a"))
        assertTrue(code("DEP-2B", "2b"))
        assertFalse(code("DEP-2A", "20"))
        assertFalse(code("DEP-2B", "20"))
        assertFalse(code("DEP-2A", "2B"))
    }

    @Test
    fun capitals_areCheckedAgainstTheDepartmentCapital() {
        assertTrue(capital("DEP-15", "Aurillac"))
        assertTrue(capital("DEP-43", "le puy en velay"))
        assertTrue(capital("DEP-971", "Basse-Terre"))
        assertFalse(capital("DEP-971", "Pointe-à-Pitre"))
        assertFalse(capital("DEP-15", "Cantal")) // le nom du département n'est pas son chef-lieu
        assertFalse(capital("DEP-43", "Le Puy")) // forme courte refusée (décision du 27 octobre 2026)
        assertTrue(capital("DEP-91", "Evry"))
        assertFalse(capital("DEP-95", "Cergy"))
    }

    @Test
    fun regionCapitals_areNotDepartmentCapitals() {
        assertTrue(capital("REG-ARA", "Lyon"))
        assertFalse(capital("REG-ARA", "Clermont-Ferrand"))
        assertTrue(name("REG-PAC", "provence alpes cote d'azur"))
        assertTrue(name("REG-PAC", "paca"))
        assertTrue(name("REG-PAC", "Région Sud"))
    }

    @Test
    fun codeNormalizer_and_officialOrder() {
        assertEquals("01", CodeNormalizer.normalize("1"))
        assertEquals("2A", CodeNormalizer.normalize(" 2a "))
        assertTrue(CodeNormalizer.order("19") < CodeNormalizer.order("2A"))
        assertTrue(CodeNormalizer.order("2A") < CodeNormalizer.order("2B"))
        assertTrue(CodeNormalizer.order("2B") < CodeNormalizer.order("21"))
        assertTrue(CodeNormalizer.order("95") < CodeNormalizer.order("971"))
    }
}
