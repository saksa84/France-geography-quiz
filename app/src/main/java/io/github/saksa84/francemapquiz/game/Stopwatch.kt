package io.github.saksa84.francemapquiz.game

/**
 * Chronomètre d'une partie, avec pause (lecture d'une fiche) et arrêt définitif (fin de partie).
 * Le temps passé en pause n'est pas compté.
 *
 * @param clock horloge en millisecondes, injectée pour pouvoir tester
 */
class Stopwatch(private val clock: () -> Long) {
    private val startMs = clock()
    private var pausedTotalMs = 0L
    private var pausedAt: Long? = null
    private var stoppedAt: Long? = null

    val isPaused: Boolean get() = pausedAt != null
    val isStopped: Boolean get() = stoppedAt != null

    /** Temps de jeu écoulé, pauses déduites. */
    fun elapsedMs(): Long = (stoppedAt ?: pausedAt ?: clock()) - startMs - pausedTotalMs

    /** Sans effet si le chronomètre est déjà en pause ou arrêté. */
    fun pause() {
        if (pausedAt == null && stoppedAt == null) pausedAt = clock()
    }

    fun resume() {
        pausedAt?.let { pausedTotalMs += clock() - it }
        pausedAt = null
    }

    /** Arrêt définitif ; une pause en cours est d'abord terminée. */
    fun stop() {
        if (stoppedAt != null) return
        resume()
        stoppedAt = clock()
    }
}
