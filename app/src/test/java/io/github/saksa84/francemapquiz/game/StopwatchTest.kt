package io.github.saksa84.francemapquiz.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {

    private var now = 10_000L
    private val clock = { now }

    @Test
    fun pausedTime_isNotCounted() {
        val w = Stopwatch(clock)
        now += 3_000
        w.pause()
        now += 60_000
        assertEquals("Figé pendant la pause", 3_000, w.elapsedMs())
        w.resume()
        now += 2_000
        assertEquals(5_000, w.elapsedMs())
    }

    @Test
    fun pauseAndResume_twice_areHarmless() {
        val w = Stopwatch(clock)
        now += 1_000
        w.pause(); now += 500; w.pause() // la seconde pause ne repart pas de zéro
        now += 500
        w.resume(); w.resume()
        now += 1_000
        assertEquals(2_000, w.elapsedMs())
    }

    @Test
    fun stop_endsAPauseAndFreezesTheTime() {
        val w = Stopwatch(clock)
        now += 4_000
        w.pause()
        now += 10_000
        w.stop()
        now += 10_000
        w.pause()
        assertFalse(w.isPaused)
        assertTrue(w.isStopped)
        assertEquals(4_000, w.elapsedMs())
    }
}
