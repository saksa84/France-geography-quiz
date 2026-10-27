package io.github.saksa84.francemapquiz.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EliminateSessionTest {

    private var now = 0L
    private val clock = { now }

    @Test
    fun wrongTap_isAMistakeAndMovesOn_withoutEliminating() {
        val s = EliminateSession(listOf("A", "B"), clock)
        assertFalse(s.tap("B")!!.correct)
        assertEquals("Une seule chance : on passe au lieu suivant", "B", s.currentTarget)
        assertEquals(1, s.errors)
        assertTrue("Le lieu manqué reste sur la carte", s.eliminated.isEmpty())
    }

    @Test
    fun correctTap_eliminatesAndMovesOn() {
        val s = EliminateSession(listOf("A", "B"), clock)
        assertTrue(s.tap("A")!!.correct)
        assertEquals(setOf("A"), s.eliminated)
        assertEquals("B", s.currentTarget)
    }

    @Test
    fun eliminatedRegionsCannotBeTappedAgain() {
        val s = EliminateSession(listOf("A", "B"), clock)
        s.tap("A")
        assertNull("Déjà éliminée : ce n'est pas une réponse", s.tap("A"))
        assertEquals(0, s.errors)
        assertEquals("B", s.currentTarget)
    }

    @Test
    fun gameEndsWhenEveryPlaceWasAskedOnce_andTimerStops() {
        val s = EliminateSession(listOf("A", "B", "C"), clock)
        now = 1_000; s.tap("A")
        now = 2_500; s.tap("C") // erreur sur B
        assertFalse(s.isFinished)
        now = 3_000; s.tap("C")
        now = 99_000
        assertTrue(s.isFinished)
        assertNull(s.currentTarget)
        assertNull(s.tap("B"))
        assertEquals(3_000, s.elapsedMs())
    }

    @Test
    fun onlyMissedPlacesRemainOnTheMapAtTheEnd() {
        val all = listOf("A", "B", "C", "D")
        val s = EliminateSession(all, clock)
        s.tap("A"); s.tap("C"); s.tap("C"); s.tap("A") // B et D manqués (A ignoré : déjà éliminé)
        assertFalse(s.isFinished)
        s.tap("B") // erreur sur D
        assertTrue(s.isFinished)
        assertEquals(listOf("B", "D"), all - s.eliminated)
    }

    @Test
    fun summary_countsSuccessesAndListsEachMistake() {
        val s = EliminateSession(listOf("A", "B", "C"), clock)
        s.tap("A")
        s.tap("C") // erreur sur B
        s.tap("C")
        val summary = s.summary()
        assertEquals(3, summary.total)
        assertEquals(2, summary.correct)
        assertEquals(listOf("B"), summary.missedTargetIds)
        assertEquals(listOf("C"), summary.mistakes.map { it.selectedId })
    }

    @Test
    fun emptyGame_isFinished() {
        val s = EliminateSession(emptyList(), clock)
        assertTrue(s.isFinished)
        assertEquals(0, s.summary().total)
    }
}
