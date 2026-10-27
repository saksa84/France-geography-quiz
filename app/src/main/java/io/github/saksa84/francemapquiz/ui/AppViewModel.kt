package io.github.saksa84.francemapquiz.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.data.AssetGeoRepository
import io.github.saksa84.francemapquiz.data.Category
import io.github.saksa84.francemapquiz.data.MapData
import io.github.saksa84.francemapquiz.game.AnswerChecker
import io.github.saksa84.francemapquiz.game.AnswerRecord
import io.github.saksa84.francemapquiz.game.CategoryLevels
import io.github.saksa84.francemapquiz.game.EliminateSession
import io.github.saksa84.francemapquiz.game.GeoCategory
import io.github.saksa84.francemapquiz.game.LevelsConfig
import io.github.saksa84.francemapquiz.game.QuestionType
import io.github.saksa84.francemapquiz.game.QuizLevel
import io.github.saksa84.francemapquiz.game.QuizLevels
import io.github.saksa84.francemapquiz.game.QuizSession
import io.github.saksa84.francemapquiz.game.QuizSummary
import io.github.saksa84.francemapquiz.progress.Progress
import io.github.saksa84.francemapquiz.progress.ProgressStore
import io.github.saksa84.francemapquiz.progress.Settings
import io.github.saksa84.francemapquiz.sound.AnswerSounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Écrans. Parcours : accueil (catégories) → catégorie (modes) → niveau et question → partie → résultat. */
enum class Screen { HOME, CATEGORY, LEARN, LEVELS, GAME, RESULT, PROGRESS, SETTINGS }

/** Modes de quiz jouables. */
enum class GameMode { FIND, TYPE, ELIMINATE }

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: MapData, val levels: Map<GeoCategory, CategoryLevels>) : LoadState {
        /** Tous les lieux jouables, toutes catégories confondues. */
        val playableIds: Set<String> get() = levels.values.flatMap { it.levels }.flatMap { it.targetIds }.toSet()
    }
    data class Failed(val message: String) : LoadState
}

/**
 * Retour bref après un toucher en mode Éliminer : le lieu touché clignote (et, en cas d'erreur,
 * le lieu demandé [targetId] est montré) avant la consigne suivante.
 */
data class Flash(val id: String, val correct: Boolean, val targetId: String)

/** Ce que l'écran de jeu affiche ; recalculé après chaque action pour que Compose redessine. */
data class GameUiState(
    val mode: GameMode,
    val category: GeoCategory,
    val question: QuestionType,
    val level: QuizLevel,
    val phase: QuizSession.Phase,
    val targetId: String?,
    val questionNumber: Int,
    val total: Int,
    val score: Int,
    /** Mode Éliminer : erreurs cumulées. */
    val errors: Int = 0,
    /** Modes Trouver et Éliminer : lieu touché mais pas encore confirmé. */
    val pendingId: String? = null,
    /** Modes Trouver et Saisir : réponse affichée pendant le retour. */
    val lastAnswer: AnswerRecord? = null,
    /** Mode Éliminer : lieux déjà trouvés. */
    val eliminated: Set<String> = emptySet(),
    val flash: Flash? = null,
    /** Modes Trouver et Saisir : fiche « En savoir plus » dépliée pendant le retour (chrono en pause). */
    val factsExpanded: Boolean = false,
)

/**
 * État de l'application, conservé quand l'écran tourne (un ViewModel survit à la recréation
 * de l'activité). Il relie les moteurs de jeu, sans Android, aux écrans Compose.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    var loadState by mutableStateOf<LoadState>(LoadState.Loading)
        private set
    var screen by mutableStateOf(Screen.HOME)
        private set
    var category by mutableStateOf(GeoCategory.DEPARTMENTS)
        private set
    var mode by mutableStateOf(GameMode.FIND)
        private set

    /** Ce qu'on demande : nom, numéro ou chef-lieu. Choisi sur l'écran des niveaux. */
    var question by mutableStateOf(QuestionType.NAME)
        private set
    var game by mutableStateOf<GameUiState?>(null)
        private set
    var summary by mutableStateOf<QuizSummary?>(null)
        private set

    /** Demande de confirmation affichée quand on veut quitter une partie en cours. */
    var confirmingQuit by mutableStateOf(false)
        private set

    /** Progression sauvegardée (meilleurs scores, erreurs fréquentes) et réglages. */
    var progress by mutableStateOf(Progress())
        private set
    var settings by mutableStateOf(Settings())
        private set

    /** La dernière partie a battu le meilleur résultat du niveau. */
    var newRecord by mutableStateOf(false)
        private set

    private val store = ProgressStore(application)

    private var level: QuizLevel? = null
    private var quiz: QuizSession? = null
    private var eliminate: EliminateSession? = null
    private var pendingId: String? = null
    private var flash: Flash? = null
    private var checker: AnswerChecker? = null
    private var factsExpanded = false
    private val sounds = AnswerSounds(application)
    private val clock = { SystemClock.elapsedRealtime() }

    /** Écran où ramène la fin d'une révision (Progression) ou d'une partie normale (niveaux). */
    private var returnToProgress = false

    init {
        viewModelScope.launch { store.progress.collect { progress = it } }
        viewModelScope.launch { store.settings.collect { settings = it } }
        viewModelScope.launch {
            loadState = withContext(Dispatchers.IO) {
                runCatching {
                    val assets = getApplication<Application>().assets
                    val data = AssetGeoRepository(assets).load()
                    val config = LevelsConfig.parse(assets.open("game/levels.json").bufferedReader().use { it.readText() })
                    // Jouable : présent dans les données ET dessiné sur la carte. Une région l'est si tous ses départements le sont.
                    val drawn = data.catalog.entities.map { it.id }.filter { data.geometry[it] != null }.toSet()
                    val regions = data.catalog.inCategory(Category.REGION)
                        .filter { region -> data.catalog.departmentsOf(region.id).let { deps -> deps.isNotEmpty() && deps.all { it.id in drawn } } }
                        .map { it.id }
                    checker = AnswerChecker(data.catalog)
                    LoadState.Ready(data, QuizLevels.buildAll(config, data.catalog, drawn + regions))
                }.getOrElse { LoadState.Failed(it.message ?: it.toString()) }
            }
        }
    }

    fun open(target: Screen) {
        screen = target
    }

    fun resetProgress() {
        viewModelScope.launch { store.reset() }
    }

    fun setAnswerSounds(enabled: Boolean) {
        viewModelScope.launch { store.setAnswerSounds(enabled) }
    }

    private fun playAnswerSound(correct: Boolean) {
        if (settings.answerSounds) sounds.play(correct)
    }

    override fun onCleared() {
        sounds.release()
    }

    fun chooseCategory(newCategory: GeoCategory) {
        category = newCategory
        // Le type de question précédent peut ne pas exister dans cette catégorie (numéro d'une région).
        val types = (loadState as? LoadState.Ready)?.levels?.get(newCategory)?.questionTypes.orEmpty()
        if (question !in types) question = types.firstOrNull() ?: QuestionType.NAME
        screen = Screen.CATEGORY
    }

    fun chooseMode(newMode: GameMode) {
        mode = newMode
        screen = Screen.LEVELS
    }

    fun chooseQuestion(newQuestion: QuestionType) {
        question = newQuestion
    }

    /** Clé de progression du mode et du type de question en cours, dans la catégorie en cours. */
    val progressKey: String get() = category.progressKey(mode.name, question)

    private val isPlaying: Boolean
        get() = screen == Screen.GAME && when (mode) {
            GameMode.FIND, GameMode.TYPE -> quiz?.phase != QuizSession.Phase.FINISHED
            GameMode.ELIMINATE -> eliminate?.isFinished == false
        }

    /**
     * Retour arrière ; renvoie false sur l'accueil pour laisser Android fermer l'application.
     * Pendant une partie, on demande d'abord confirmation : un appui involontaire ne doit pas la perdre.
     */
    fun back(): Boolean {
        if (isPlaying) {
            confirmingQuit = true
            return true
        }
        screen = when (screen) {
            Screen.HOME -> return false
            Screen.CATEGORY, Screen.PROGRESS, Screen.SETTINGS -> Screen.HOME
            Screen.LEARN, Screen.LEVELS -> Screen.CATEGORY
            Screen.GAME, Screen.RESULT -> if (returnToProgress) Screen.PROGRESS else Screen.LEVELS
        }
        return true
    }

    fun quitGame() {
        confirmingQuit = false
        quiz = null
        eliminate = null
        screen = if (returnToProgress) Screen.PROGRESS else Screen.LEVELS
    }

    fun keepPlaying() {
        confirmingQuit = false
    }

    fun elapsedMs(): Long = when (mode) {
        GameMode.FIND, GameMode.TYPE -> quiz?.elapsedMs()
        GameMode.ELIMINATE -> eliminate?.elapsedMs()
    } ?: 0L

    fun startLevel(newLevel: QuizLevel) {
        returnToProgress = newLevel.id.startsWith(REVIEW_PREFIX)
        level = newLevel
        quiz = null
        eliminate = null
        when (mode) {
            GameMode.FIND, GameMode.TYPE -> quiz = QuizSession.shuffled(newLevel.targetIds, clock)
            GameMode.ELIMINATE -> eliminate = EliminateSession.shuffled(newLevel.targetIds, clock)
        }
        pendingId = null
        flash = null
        factsExpanded = false
        summary = null
        refresh()
        screen = Screen.GAME
    }

    /**
     * Lieu correspondant à une forme touchée : le département lui-même, ou sa région dans la
     * catégorie Régions (une région n'a pas de tracé propre : c'est l'union de ses départements).
     */
    fun ownerOf(shapeId: String): String {
        val catalog = (loadState as? LoadState.Ready)?.data?.catalog ?: return shapeId
        return if (category == GeoCategory.REGIONS) catalog.regionOf(shapeId)?.id ?: shapeId else shapeId
    }

    /**
     * Toucher la carte (Trouver et Éliminer) : sélectionne, et un second toucher sur le même
     * lieu confirme, comme le bouton Confirmer.
     */
    fun tap(shapeId: String?) {
        val lvl = level ?: return
        val id = shapeId?.let(::ownerOf)
        if (id == null || id !in lvl.targetIds) return
        when (mode) {
            GameMode.FIND -> {
                if (quiz?.phase != QuizSession.Phase.ASKING) return
                if (id == pendingId) confirm() else {
                    pendingId = id
                    refresh()
                }
            }
            GameMode.ELIMINATE -> {
                val s = eliminate ?: return
                if (flash != null || s.isFinished || id in s.eliminated) return
                if (id == pendingId) confirm() else {
                    pendingId = id
                    refresh()
                }
            }
            GameMode.TYPE -> Unit // en mode Saisir, la carte sert seulement à montrer le lieu
        }
    }

    fun confirm() {
        val selected = pendingId ?: return
        pendingId = null
        when (mode) {
            GameMode.ELIMINATE -> answerEliminate(selected)
            else -> {
                quiz?.answer(selected)?.let { playAnswerSound(it.correct) }
                refresh()
            }
        }
    }

    /** Mode Saisir : valide le texte tapé (nom, numéro ou chef-lieu selon la question). */
    fun submitTyped(text: String) {
        val s = quiz ?: return
        val target = s.currentTarget ?: return
        if (text.isBlank()) return
        s.answer(selectedId = null, typed = text.trim(), correct = checker?.isCorrect(target, text, question) == true)
            ?.let { playAnswerSound(it.correct) }
        refresh()
    }

    /** Mode Saisir : passe la question (comptée comme une erreur, sans son). */
    fun skip() {
        quiz?.answer(selectedId = null, typed = null, correct = false)
        refresh()
    }

    /** Déplie ou replie la fiche du lieu pendant le retour ; le chrono est en pause tant qu'elle est ouverte. */
    fun setFactsExpanded(expanded: Boolean) {
        val s = quiz ?: return
        if (s.phase != QuizSession.Phase.FEEDBACK) return
        factsExpanded = expanded
        if (expanded) s.pause() else s.resume()
        refresh()
    }

    fun next() {
        val s = quiz ?: return
        factsExpanded = false
        s.next()
        if (s.phase == QuizSession.Phase.FINISHED) finish(s.summary())
        refresh()
    }

    /**
     * Éliminer : la réponse confirmée, juste ou fausse. Pendant le clignotement, la consigne
     * reste affichée et la carte ne répond pas ; ensuite vient la consigne suivante. Une erreur reste
     * affichée plus longtemps (1,5 s), pour voir où était le lieu demandé.
     */
    private fun answerEliminate(id: String) {
        val s = eliminate ?: return
        if (flash != null) return
        val target = s.currentTarget ?: return
        val record = s.tap(id) ?: return
        playAnswerSound(record.correct)
        val current = Flash(id, record.correct, target)
        flash = current
        refresh()
        viewModelScope.launch {
            delay(if (record.correct) 600 else 1_500)
            if (flash == current) {
                flash = null
                refresh()
            }
            if (s.isFinished && eliminate === s && screen == Screen.GAME) finish(s.summary())
        }
    }

    private fun finish(result: QuizSummary) {
        val lvl = level
        if (lvl != null) {
            // « Rejouer les erreurs » ne porte que sur une partie du niveau : pas de record, mais les erreurs comptent.
            val isReplay = lvl.id.endsWith(MISTAKES_SUFFIX)
            val outcome = progress.record(progressKey, lvl.id.removeSuffix(MISTAKES_SUFFIX), result, question.name, countsForBest = !isReplay)
            progress = outcome.progress
            newRecord = outcome.newRecord
            viewModelScope.launch { store.save(outcome.progress) }
        }
        summary = result
        screen = Screen.RESULT
    }

    fun replay() {
        level?.let(::startLevel)
    }

    /**
     * Révision (mode Trouver) des erreurs les plus fréquentes d'une catégorie et d'un type de question :
     * « les numéros de départements que je rate le plus ».
     */
    fun review(reviewCategory: GeoCategory, reviewQuestion: QuestionType, ids: List<String>) {
        val ready = loadState as? LoadState.Ready ?: return
        val playable = ids.filter { it in ready.playableIds }
        if (playable.isEmpty()) return
        category = reviewCategory
        question = reviewQuestion
        mode = GameMode.FIND
        val name = getApplication<Application>().getString(R.string.review_level)
        startLevel(QuizLevel("$REVIEW_PREFIX$MISTAKES_SUFFIX", name, section = name, targetIds = playable))
    }

    /** Nouvelle partie limitée aux lieux manqués. */
    fun replayMistakes() {
        val lvl = level ?: return
        val missed = summary?.missedTargetIds.orEmpty()
        if (missed.isEmpty()) return
        val baseId = lvl.id.removeSuffix(MISTAKES_SUFFIX)
        startLevel(lvl.copy(id = baseId + MISTAKES_SUFFIX, targetIds = missed))
    }

    private companion object {
        const val MISTAKES_SUFFIX = "-mistakes"
        const val REVIEW_PREFIX = "review"
    }

    private fun refresh() {
        val lvl = level ?: return
        game = when (mode) {
            GameMode.FIND, GameMode.TYPE -> quiz?.let { s ->
                GameUiState(
                    mode = mode,
                    category = category,
                    question = question,
                    level = lvl,
                    phase = s.phase,
                    targetId = s.currentTarget,
                    questionNumber = (s.index + 1).coerceAtMost(s.total),
                    total = s.total,
                    score = s.score,
                    pendingId = pendingId,
                    lastAnswer = s.lastAnswer,
                    factsExpanded = factsExpanded,
                )
            }
            GameMode.ELIMINATE -> eliminate?.let { s ->
                GameUiState(
                    mode = mode,
                    category = category,
                    question = question,
                    level = lvl,
                    phase = if (s.isFinished && flash == null) QuizSession.Phase.FINISHED else QuizSession.Phase.ASKING,
                    // Pendant le clignotement, on garde la consigne à laquelle on vient de répondre.
                    targetId = flash?.targetId ?: s.currentTarget,
                    questionNumber = (s.asked + 1).coerceAtMost(s.total),
                    total = s.total,
                    score = s.eliminated.size,
                    errors = s.errors,
                    eliminated = s.eliminated,
                    pendingId = pendingId,
                    flash = flash,
                )
            }
        }
    }
}
