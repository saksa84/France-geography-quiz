package io.github.saksa84.francemapquiz.game

import kotlin.random.Random

/** Réponse donnée à une question : conservée en entier pour l'écran de résultat et la révision des erreurs. */
data class AnswerRecord(
    val targetId: String,
    /** Lieu touché (modes Trouver, Éliminer), ou null si rien n'a été touché. */
    val selectedId: String?,
    /** Moment de la réponse, en ms depuis le début de la partie. */
    val atMs: Long,
    /** Texte tapé (mode Saisir), tel que l'utilisateur l'a écrit. */
    val typed: String? = null,
    val correct: Boolean = selectedId == targetId,
)

/**
 * Bilan d'une partie. Une question peut en principe avoir plusieurs réponses :
 * elle est réussie si sa première réponse est juste.
 */
data class QuizSummary(
    val total: Int,
    val durationMs: Long,
    val answers: List<AnswerRecord>,
) {
    /** Lieux trouvés du premier coup. */
    val correct: Int get() = answers.groupBy { it.targetId }.count { (_, tries) -> tries.first().correct }
    val mistakes: List<AnswerRecord> get() = answers.filter { !it.correct }
    /** Lieux à revoir : au moins une erreur. */
    val missedTargetIds: List<String> get() = mistakes.map { it.targetId }.distinct()
    val percent: Double get() = if (total == 0) 0.0 else 100.0 * correct / total
}

/**
 * Une partie de quiz, indépendante de l'écran et du mode : une liste de lieux à trouver,
 * une seule réponse par question, puis la question suivante.
 *
 * Cycle d'une question : [Phase.ASKING] → [answer] → [Phase.FEEDBACK] → [next] → question suivante,
 * jusqu'à [Phase.FINISHED]. Une liste vide donne directement une partie terminée.
 *
 * @param clock horloge en millisecondes, injectée pour pouvoir tester le chronomètre
 */
class QuizSession(
    val targetIds: List<String>,
    private val clock: () -> Long,
) {
    enum class Phase { ASKING, FEEDBACK, FINISHED }

    private val stopwatch = Stopwatch(clock)
    private val records = mutableListOf<AnswerRecord>()

    var index: Int = 0
        private set

    var phase: Phase = if (targetIds.isEmpty()) Phase.FINISHED else Phase.ASKING
        private set

    init {
        require(targetIds.toSet().size == targetIds.size) { "Un lieu ne peut être demandé qu'une fois par partie" }
        if (targetIds.isEmpty()) stopwatch.stop()
    }

    val total: Int get() = targetIds.size
    val currentTarget: String? get() = targetIds.getOrNull(index)?.takeIf { phase != Phase.FINISHED }
    val score: Int get() = records.count { it.correct }
    val answers: List<AnswerRecord> get() = records.toList()

    /** Dernière réponse, affichée pendant [Phase.FEEDBACK]. */
    val lastAnswer: AnswerRecord? get() = records.lastOrNull()

    /** Temps écoulé, pauses déduites ; figé à la dernière réponse. */
    fun elapsedMs(): Long = stopwatch.elapsedMs()

    val isPaused: Boolean get() = stopwatch.isPaused

    /** Met le chronomètre en pause, par exemple pendant la lecture d'une fiche. */
    fun pause() {
        if (phase != Phase.FINISHED) stopwatch.pause()
    }

    fun resume() = stopwatch.resume()

    /**
     * Enregistre la réponse à la question en cours. Ignorée si on n'attend pas de réponse.
     * @param typed texte tapé (mode Saisir) ; [correct] doit alors être fourni par [AnswerChecker]
     */
    fun answer(selectedId: String?, typed: String? = null, correct: Boolean? = null): AnswerRecord? {
        if (phase != Phase.ASKING) return null
        val target = targetIds[index]
        stopwatch.resume()
        val record = AnswerRecord(target, selectedId, stopwatch.elapsedMs(), typed, correct ?: (selectedId == target))
        records += record
        if (index == targetIds.lastIndex) stopwatch.stop()
        phase = Phase.FEEDBACK
        return record
    }

    /** Passe à la question suivante après le retour visuel ; relance le chronomètre s'il était en pause. */
    fun next() {
        if (phase != Phase.FEEDBACK) return
        stopwatch.resume()
        index++
        phase = if (index >= targetIds.size) Phase.FINISHED else Phase.ASKING
    }

    fun summary(): QuizSummary = QuizSummary(total, elapsedMs(), answers)

    companion object {
        /** Partie sur des lieux tirés dans un ordre aléatoire ; [random] est injectable pour des tests reproductibles. */
        fun shuffled(ids: Collection<String>, clock: () -> Long, random: Random = Random.Default) =
            QuizSession(ids.distinct().shuffled(random), clock)
    }
}
