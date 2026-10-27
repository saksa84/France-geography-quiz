package io.github.saksa84.francemapquiz.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizSessionTest {

    /** Horloge contrôlée par le test. */
    private var now = 1_000L
    private val clock = { now }

    private fun session(vararg ids: String) = QuizSession(ids.toList(), clock)

    @Test
    fun correctAnswer_scoresAndShowsFeedback() {
        val s = session("DEP-15", "DEP-75")
        assertEquals("DEP-15", s.currentTarget)
        val record = s.answer("DEP-15")!!
        assertTrue(record.correct)
        assertEquals(1, s.score)
        assertEquals(QuizSession.Phase.FEEDBACK, s.phase)
    }

    @Test
    fun wrongAnswer_recordsWhatWasTouched() {
        val s = session("DEP-15")
        val record = s.answer("DEP-63")!!
        assertFalse(record.correct)
        assertEquals("DEP-63", record.selectedId)
        assertEquals(0, s.score)
    }

    @Test
    fun onlyOneAnswerPerQuestion() {
        val s = session("DEP-15", "DEP-75")
        s.answer("DEP-63")
        assertNull("Une seule chance", s.answer("DEP-15"))
        assertEquals(0, s.score)
        assertEquals(1, s.answers.size)
    }

    @Test
    fun next_goesThroughAllQuestionsThenFinishes() {
        val s = session("A", "B", "C")
        listOf("A", "X", "C").forEach { answer ->
            s.answer(answer)
            s.next()
        }
        assertEquals(QuizSession.Phase.FINISHED, s.phase)
        assertNull(s.currentTarget)
        assertEquals(2, s.score)
    }

    @Test
    fun next_isIgnoredBeforeAnswering() {
        val s = session("A", "B")
        s.next()
        assertEquals("A", s.currentTarget)
    }

    @Test
    fun emptySession_isImmediatelyFinished() {
        val s = session()
        assertEquals(QuizSession.Phase.FINISHED, s.phase)
        assertNull(s.answer("A"))
        assertEquals(0.0, s.summary().percent, 0.0)
    }

    @Test
    fun timer_runsDuringTheGameAndStopsAtTheLastAnswer() {
        val s = session("A", "B")
        now += 2_500
        assertEquals(2_500, s.elapsedMs())
        s.answer("A"); s.next()
        now += 1_000
        s.answer("B")
        now += 60_000 // temps passé sur l'écran de résultat : ne compte pas
        assertEquals(3_500, s.elapsedMs())
        assertEquals(3_500, s.summary().durationMs)
    }

    @Test
    fun summary_listsMistakesInOrder() {
        val s = session("A", "B", "C")
        s.answer("A"); s.next()
        s.answer("X"); s.next()
        s.answer(null); s.next()
        val summary = s.summary()
        assertEquals(3, summary.total)
        assertEquals(1, summary.correct)
        assertEquals(listOf("B", "C"), summary.mistakes.map { it.targetId })
        assertEquals(listOf("X", null), summary.mistakes.map { it.selectedId })
        assertEquals(100.0 / 3, summary.percent, 1e-9)
    }

    @Test
    fun replayingMistakes_asksOnlyTheMissedPlaces() {
        val first = session("A", "B", "C")
        first.answer("A"); first.next()
        first.answer("X"); first.next()
        first.answer("Y"); first.next()
        val replay = QuizSession.shuffled(first.summary().mistakes.map { it.targetId }, clock, Random(1))
        assertEquals(setOf("B", "C"), replay.targetIds.toSet())
    }

    @Test
    fun shuffled_isReproducibleWithASeedAndAsksEachPlaceOnce() {
        val ids = (1..34).map { "P$it" }
        val a = QuizSession.shuffled(ids, clock, Random(42)).targetIds
        val b = QuizSession.shuffled(ids, clock, Random(42)).targetIds
        assertEquals(a, b)
        assertEquals(ids.toSet(), a.toSet())
        assertEquals(34, a.size)
    }

    @Test
    fun typedAnswers_keepTheTextAndTheCheckerVerdict() {
        val s = session("DEP-15", "DEP-2A")
        assertTrue(s.answer(selectedId = null, typed = "Sìchuān", correct = true)!!.correct)
        s.next()
        val wrong = s.answer(selectedId = null, typed = "Tibey", correct = false)!!
        assertFalse(wrong.correct)
        assertEquals("Tibey", s.summary().mistakes.single().typed)
        assertEquals(1, s.score)
    }

    @Test
    fun skippedQuestion_isAMistake() {
        val s = session("A")
        s.answer(selectedId = null, typed = null, correct = false)
        assertEquals(listOf("A"), s.summary().missedTargetIds)
    }

    @Test
    fun readingTheFacts_pausesTheTimerUntilTheNextQuestion() {
        val s = session("A", "B")
        now += 2_000
        s.answer("A")
        s.pause()
        assertTrue(s.isPaused)
        now += 30_000 // lecture de la fiche
        assertEquals(2_000, s.elapsedMs())
        s.next()
        assertFalse("Question suivante : le chrono repart", s.isPaused)
        now += 1_000
        assertEquals(3_000, s.elapsedMs())
        assertEquals(3_000, s.answer("B")!!.atMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateTargets_areRejected() {
        session("A", "A")
    }
}
