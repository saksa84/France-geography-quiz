package io.github.saksa84.francemapquiz.progress

import io.github.saksa84.francemapquiz.game.AnswerRecord
import io.github.saksa84.francemapquiz.game.QuizSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    /** Partie fictive : [right] lieux justes puis [wrong] lieux manqués, en [timeMs]. */
    private fun game(right: List<String>, wrong: List<String>, timeMs: Long) = QuizSummary(
        total = right.size + wrong.size,
        durationMs = timeMs,
        answers = right.map { AnswerRecord(it, it, 0) } + wrong.map { AnswerRecord(it, "X", 0) },
    )

    @Test
    fun firstGame_isARecord() {
        val outcome = Progress().record("FIND", "north-mainland", game(listOf("A", "B"), listOf("C"), 10_000), "NAME")
        assertTrue(outcome.newRecord)
        assertEquals(LevelBest(2, 3, 10_000, 1), outcome.progress.bestFor("FIND", "north-mainland"))
        assertEquals(1, outcome.progress.gamesPlayed)
    }

    @Test
    fun moreCorrectAnswers_beatsAFasterTime() {
        val p = Progress().record("FIND", "L", game(listOf("A"), listOf("B"), 5_000), "NAME").progress
        val outcome = p.record("FIND", "L", game(listOf("A", "B"), emptyList(), 50_000), "NAME")
        assertTrue(outcome.newRecord)
        assertTrue(outcome.progress.bestFor("FIND", "L")!!.perfect)
    }

    @Test
    fun sameScore_fasterIsARecord_slowerIsNot() {
        val p = Progress().record("FIND", "L", game(listOf("A", "B"), emptyList(), 20_000), "NAME").progress
        assertFalse(p.record("FIND", "L", game(listOf("A", "B"), emptyList(), 25_000), "NAME").newRecord)
        val faster = p.record("FIND", "L", game(listOf("A", "B"), emptyList(), 15_000), "NAME")
        assertTrue(faster.newRecord)
        assertEquals(15_000, faster.progress.bestFor("FIND", "L")!!.timeMs)
    }

    @Test
    fun worseGame_keepsThePreviousBest() {
        val p = Progress().record("FIND", "L", game(listOf("A", "B"), emptyList(), 20_000), "NAME").progress
        val outcome = p.record("FIND", "L", game(listOf("A"), listOf("B"), 1_000), "NAME")
        assertFalse(outcome.newRecord)
        assertEquals(2, outcome.progress.bestFor("FIND", "L")!!.correct)
    }

    @Test
    fun modesAndLevelsAreKeptSeparately() {
        val p = Progress().record("FIND", "L", game(listOf("A"), emptyList(), 1_000), "NAME").progress
        assertNull(p.bestFor("TYPE", "L"))
        assertNull(p.bestFor("FIND", "other"))
    }

    @Test
    fun mistakesReplay_doesNotBecomeTheLevelBest_butCountsMisses() {
        val outcome = Progress().record("FIND", "L", game(listOf("A"), listOf("B"), 1_000), "NAME", countsForBest = false)
        assertFalse(outcome.newRecord)
        assertNull(outcome.progress.bestFor("FIND", "L"))
        assertEquals(mapOf("NAME:B" to 1), outcome.progress.missCounts)
    }

    @Test
    fun missCounts_accumulateOncePerGame() {
        // Mode Éliminer : trois erreurs sur B dans la même partie ne comptent qu'une fois.
        val eliminate = QuizSummary(2, 1_000, listOf(AnswerRecord("B", "X", 0), AnswerRecord("B", "Y", 0), AnswerRecord("B", "B", 0), AnswerRecord("A", "A", 0)))
        var p = Progress().record("ELIMINATE", "L", eliminate, "NAME").progress
        p = p.record("FIND", "L", game(emptyList(), listOf("B", "C"), 1_000), "NAME").progress
        assertEquals(mapOf("NAME:B" to 2, "NAME:C" to 1), p.missCounts)
        assertEquals(listOf("NAME:B" to 2, "NAME:C" to 1), p.mostMissed(5))
    }

    @Test
    fun missCounts_areSeparatedByQuestionType() {
        // Manquer le numéro du Cantal n'est pas manquer son nom.
        var p = Progress().record("FIND_NAME", "L", game(emptyList(), listOf("DEP-15"), 1_000), "NAME").progress
        p = p.record("FIND_CODE", "L", game(emptyList(), listOf("DEP-15"), 1_000), "CODE").progress
        assertEquals(mapOf("NAME:DEP-15" to 1, "CODE:DEP-15" to 1), p.missCounts)
        assertEquals("CODE" to "DEP-15", Progress.parseMissKey("CODE:DEP-15"))
        assertNull(Progress.parseMissKey("illisible"))
    }

    @Test
    fun completion_sumsBestScoresOverLevels() {
        var p = Progress()
        p = p.record("FIND", "north", game(listOf("A", "B", "C"), listOf("D"), 1_000), "NAME").progress
        val completion = p.completion("FIND", listOf("north" to 4, "south" to 4))
        assertEquals(3f / 8, completion, 1e-6f)
    }

    @Test
    fun jsonRoundTrip_andTolerantReading() {
        val p = Progress().record("FIND", "L", game(listOf("A"), listOf("B"), 1_234), "NAME").progress
        assertEquals(p, Progress.fromJson(p.toJson()))
        assertEquals(Progress(), Progress.fromJson("pas du json"))
        assertEquals(Progress(), Progress.fromJson(null))
    }
}
