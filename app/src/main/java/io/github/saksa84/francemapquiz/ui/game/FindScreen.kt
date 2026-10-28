package io.github.saksa84.francemapquiz.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.data.MapData
import io.github.saksa84.francemapquiz.game.QuizSession
import io.github.saksa84.francemapquiz.ui.GameUiState
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.map.FranceMap
import io.github.saksa84.francemapquiz.ui.map.ShapeStyle

/**
 * Mode Trouver : « Trouver : Cantal », « Trouver : 15 » ou « Trouver : Aurillac ». Toucher sélectionne,
 * Confirmer (ou toucher à nouveau le même lieu) valide. Une seule chance ; ensuite la bonne réponse est montrée.
 */
@Composable
fun FindScreen(
    data: MapData,
    state: GameUiState,
    elapsedMs: () -> Long,
    onTap: (String?) -> Unit,
    onConfirm: () -> Unit,
    onNext: () -> Unit,
    onFactsExpanded: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val shownMs = rememberElapsed(elapsedMs)
    val inLevel = remember(state.level) { state.level.targetIds.toSet() }
    val focus = remember(state.level) { levelFocus(data, state.level.targetIds) }
    val capitals = remember(state.level, state.question) { capitalsShown(data, state.question, state.level.targetIds) }
    val answer = state.lastAnswer.takeIf { state.phase == QuizSession.Phase.FEEDBACK }
    val asking = state.phase == QuizSession.Phase.ASKING

    GameLayout(
        topBar = {
            GameTopBar(
                shownMs,
                stringResource(R.string.find_points, state.score),
                stringResource(R.string.find_round, state.questionNumber, state.total),
                onBack,
                paused = state.factsExpanded,
            )
        },
        feedback = {
            when {
                answer == null -> FeedbackText(null, QuizColors.OnDark)
                answer.correct -> FeedbackText(stringResource(R.string.find_correct), QuizColors.Correct)
                else -> FeedbackText(
                    stringResource(R.string.find_wrong, answer.selectedId?.let { data.catalog[it]?.displayName } ?: "?"),
                    QuizColors.Wrong,
                )
            }
        },
        map = { modifier ->
            FranceMap(
                geometry = data.geometry,
                styleOf = { id -> findStyle(id, state, inLevel, data, capitals) },
                selectedIds = shapesOf(data, state.pendingId),
                onShapeTap = onTap,
                modifier = modifier,
                focus = focus,
                seaColor = QuizColors.Sea,
                contextColor = QuizColors.Context,
                borderColor = QuizColors.Border,
                selectedBorderColor = QuizColors.Accent,
            )
        },
        overlay = { if (answer != null) AnswerCard(data, answer.targetId, state.factsExpanded, onFactsExpanded) },
        bottom = {
            PromptWithCaption(stringResource(R.string.find_prompt, promptValue(data, state)), promptCaption(state))
            val isLast = state.questionNumber >= state.total
            GameButton(
                stringResource(
                    when {
                        asking -> R.string.find_confirm
                        isLast -> R.string.find_results
                        else -> R.string.find_next
                    },
                ),
                onClick = if (asking) onConfirm else onNext,
                enabled = !asking || state.pendingId != null,
            )
            GameProgress(state.questionNumber - if (asking) 1 else 0, state.total)
        },
    )
}

/**
 * Couleurs de la carte en jeu : terres neutres (aucun indice), hors niveau grisées,
 * sélection en attente en jaune ; pendant le retour, bonne réponse en vert et erreur en rouge.
 */
private fun findStyle(shapeId: String, state: GameUiState, inLevel: Set<String>, data: MapData, capitals: Set<String>): ShapeStyle? {
    val id = ownerOf(data, state.category, shapeId)
    val answer = state.lastAnswer.takeIf { state.phase == QuizSession.Phase.FEEDBACK }
    val stateColor = when {
        answer != null && id == answer.targetId -> QuizColors.Correct
        answer != null && id == answer.selectedId -> QuizColors.Wrong
        id == state.pendingId -> QuizColors.Accent
        else -> null
    }
    // Réticule (si le lieu est minuscule) sur la sélection en attente et, pendant le retour,
    // sur la bonne réponse et l'erreur.
    val emphasized = id == state.pendingId || (answer != null && (id == answer.targetId || id == answer.selectedId))
    return gameStyle(shapeId, data, id, inLevel, stateColor, emphasized, capitals, state.category)
}
