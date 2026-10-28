package io.github.saksa84.francemapquiz.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.data.FeatureType
import io.github.saksa84.francemapquiz.data.MapData
import io.github.saksa84.francemapquiz.ui.GameUiState
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.map.FranceMap
import io.github.saksa84.francemapquiz.ui.map.ShapeStyle

/**
 * Mode Éliminer : une consigne à la fois, une seule chance. Comme dans Trouver, toucher sélectionne
 * et Confirmer (ou toucher à nouveau le même lieu) valide. Un lieu trouvé s'efface et n'est plus
 * touchable ; un lieu manqué reste sur la carte. À la fin, il ne reste que les lieux manqués.
 */
@Composable
fun EliminateScreen(
    data: MapData,
    state: GameUiState,
    elapsedMs: () -> Long,
    onTap: (String?) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val shownMs = rememberElapsed(elapsedMs)
    val inLevel = remember(state.level) { state.level.targetIds.toSet() }
    val focus = remember(state.level) { levelFocus(data, state.level.targetIds) }
    val capitals = remember(state.level, state.question) { capitalsShown(data, state.question, state.level.targetIds) }

    GameLayout(
        topBar = {
            GameTopBar(
                shownMs,
                stringResource(R.string.eliminate_found, state.score, state.total),
                stringResource(R.string.eliminate_errors, state.errors),
                onBack,
            )
        },
        feedback = {
            val flash = state.flash
            when {
                flash == null -> FeedbackText(null, QuizColors.OnDark)
                flash.correct -> FeedbackText(stringResource(R.string.find_correct), QuizColors.Correct)
                else -> FeedbackText(
                    stringResource(R.string.eliminate_wrong, data.catalog[flash.id]?.displayName ?: "?"),
                    QuizColors.Wrong,
                )
            }
        },
        map = { modifier ->
            FranceMap(
                geometry = data.geometry,
                styleOf = { id -> eliminateStyle(id, state, inLevel, data, capitals) },
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
        overlay = {},
        bottom = {
            PromptWithCaption(stringResource(R.string.find_prompt, promptValue(data, state)), promptCaption(state))
            GameButton(stringResource(R.string.find_confirm), onConfirm, enabled = state.flash == null && state.pendingId != null)
            GameProgress(state.questionNumber - if (state.flash == null) 1 else 0, state.total)
        },
    )
}

/**
 * Lieux trouvés effacés (presque transparents, plus touchables) ; clignotement vif sur le dernier
 * toucher : vert si juste, rouge si faux, et le lieu demandé en vert après une erreur.
 */
private fun eliminateStyle(shapeId: String, state: GameUiState, inLevel: Set<String>, data: MapData, capitals: Set<String>): ShapeStyle? {
    val id = ownerOf(data, state.category, shapeId)
    // Le chef-lieu d'un lieu éliminé disparaît avec lui.
    val capitalOf = data.catalog[shapeId]?.takeIf { it.type == FeatureType.PREFECTURE }?.parentId
    if (capitalOf != null && ownerOf(data, state.category, capitalOf) in state.eliminated) return null
    val flash = state.flash
    val touched = flash?.id == id
    val missedTarget = flash != null && !flash.correct && flash.targetId == id
    val stateColor = when {
        touched -> if (flash?.correct == true) QuizColors.Correct else QuizColors.Wrong
        missedTarget -> QuizColors.Correct
        id == state.pendingId -> QuizColors.Accent
        else -> null
    }
    val emphasized = touched || missedTarget || id == state.pendingId
    val style = gameStyle(shapeId, data, id, inLevel, stateColor, emphasized, capitals, state.category) ?: return null
    if (id !in state.eliminated || touched) return style
    return style.copy(fill = QuizColors.Eliminated, smallMarker = false, tappable = false, lineWidthDp = 2f)
}
