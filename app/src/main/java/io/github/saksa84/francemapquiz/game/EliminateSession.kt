package io.github.saksa84.francemapquiz.game

import kotlin.random.Random

/**
 * Partie du mode Éliminer : une consigne à la fois, **une seule chance** par lieu (la confirmation du choix est gérée par l'écran).
 * Un lieu trouvé est éliminé (plus touchable) ; un lieu manqué reste sur la carte et l'on passe
 * quand même au suivant. La partie finit quand chaque lieu a été demandé une fois : il ne reste
 * alors sur la carte que les lieux manqués.
 *
 * @param clock horloge en millisecondes, injectée pour pouvoir tester le chronomètre
 */
class EliminateSession(
    val targetIds: List<String>,
    clock: () -> Long,
) {
    private val stopwatch = Stopwatch(clock)
    private val records = mutableListOf<AnswerRecord>()
    private val found = mutableSetOf<String>()

    init {
        require(targetIds.toSet().size == targetIds.size) { "Un lieu ne peut être demandé qu'une fois par partie" }
        if (targetIds.isEmpty()) stopwatch.stop()
    }

    val total: Int get() = targetIds.size
    /** Nombre de lieux déjà demandés. */
    val asked: Int get() = records.size
    val isFinished: Boolean get() = asked == targetIds.size
    val currentTarget: String? get() = targetIds.getOrNull(asked)
    val eliminated: Set<String> get() = found.toSet()
    val errors: Int get() = records.count { !it.correct }
    val lastAnswer: AnswerRecord? get() = records.lastOrNull()

    fun elapsedMs(): Long = stopwatch.elapsedMs()

    /**
     * Toucher un lieu : c'est la réponse à la consigne en cours, juste ou fausse, puis consigne suivante.
     * Ignoré si la partie est finie ou si le lieu est déjà éliminé (il n'est plus sur la carte).
     */
    fun tap(id: String): AnswerRecord? {
        val target = currentTarget ?: return null
        if (id in found) return null
        val record = AnswerRecord(target, id, stopwatch.elapsedMs())
        records += record
        if (record.correct) found += id
        if (isFinished) stopwatch.stop()
        return record
    }

    fun summary(): QuizSummary = QuizSummary(total, elapsedMs(), records.toList())

    companion object {
        fun shuffled(ids: Collection<String>, clock: () -> Long, random: Random = Random.Default) =
            EliminateSession(ids.distinct().shuffled(random), clock)
    }
}
