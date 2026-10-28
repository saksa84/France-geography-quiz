package io.github.saksa84.francemapquiz.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.data.MapData
import io.github.saksa84.francemapquiz.game.QuestionType
import io.github.saksa84.francemapquiz.game.QuizSession
import io.github.saksa84.francemapquiz.ui.GameUiState
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.map.FranceMap
import io.github.saksa84.francemapquiz.ui.map.ShapeStyle

/**
 * Mode Saisir : le lieu est coloré sur la carte ; on tape son nom, son numéro ou son chef-lieu,
 * sans suggestion. Une seule chance (ou « Passer ») ; ensuite la bonne réponse est affichée.
 * Pour les numéros, un petit pavé (0-9, A, B) remplace le clavier : il ne cache pas la carte.
 */
@Composable
fun TypeScreen(
    data: MapData,
    state: GameUiState,
    elapsedMs: () -> Long,
    onSubmit: (String) -> Unit,
    onSkip: () -> Unit,
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
    // Texte remis à zéro à chaque question ; conservé si l'écran tourne.
    var typed by rememberSaveable(state.questionNumber) { mutableStateOf("") }

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
            val expected = answer?.targetId?.let { data.catalog[it] }?.let { state.question.valueOf(it, data.catalog) } ?: ""
            when {
                answer == null -> FeedbackText(null, QuizColors.OnDark)
                answer.correct -> FeedbackText(stringResource(R.string.type_correct, expected), QuizColors.Correct)
                answer.typed == null -> FeedbackText(stringResource(R.string.type_skipped, expected), QuizColors.Wrong)
                else -> FeedbackText(stringResource(R.string.type_wrong, expected, answer.typed), QuizColors.Wrong)
            }
        },
        map = { modifier ->
            FranceMap(
                geometry = data.geometry,
                styleOf = { id -> typeStyle(id, state, inLevel, data, capitals) },
                selectedIds = emptySet(),
                onShapeTap = {},
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
            if (asking) {
                PromptText(stringResource(typePrompt(state.question)))
                Spacer(Modifier.height(8.dp))
                if (state.question == QuestionType.CODE) {
                    CodeKeypad(typed, onChange = { typed = it }, onSkip = onSkip, onSubmit = { onSubmit(typed) })
                } else {
                    TextAnswer(typed, state.questionNumber, onChange = { typed = it }, onSkip = onSkip, onSubmit = { onSubmit(typed) })
                }
            } else {
                val isLast = state.questionNumber >= state.total
                GameButton(stringResource(if (isLast) R.string.find_results else R.string.find_next), onNext)
            }
            GameProgress(state.questionNumber - if (asking) 1 else 0, state.total)
        },
    )
}

private fun typePrompt(question: QuestionType): Int = when (question) {
    QuestionType.NAME -> R.string.type_prompt_name
    QuestionType.CODE -> R.string.type_prompt_code
    QuestionType.CAPITAL -> R.string.type_prompt_capital
}

/** Champ de saisie d'un nom, avec le clavier du téléphone (sans correcteur ni suggestions). */
@Composable
private fun TextAnswer(typed: String, questionNumber: Int, onChange: (String) -> Unit, onSkip: () -> Unit, onSubmit: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(questionNumber) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = typed,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.type_placeholder)) },
        textStyle = TextStyle(fontSize = 22.sp, color = QuizColors.OnDark),
        // Pas de correcteur ni de suggestions du clavier : ils donneraient la réponse.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = QuizColors.Accent,
            unfocusedBorderColor = QuizColors.Muted,
            cursorColor = QuizColors.Accent,
            focusedPlaceholderColor = QuizColors.Muted,
            unfocusedPlaceholderColor = QuizColors.Muted,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameButton(stringResource(R.string.type_skip), onSkip, Modifier.weight(1f))
        GameButton(stringResource(R.string.type_validate), onSubmit, Modifier.weight(2f), enabled = typed.isNotBlank())
    }
}

/**
 * Pavé des numéros : chiffres, A et B (pour 2A et 2B), effacement. Trois caractères au plus (976).
 * Passer et Valider sont sur la dernière ligne, comme les autres modes.
 */
@Composable
private fun CodeKeypad(typed: String, onChange: (String) -> Unit, onSkip: () -> Unit, onSubmit: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp).background(QuizColors.Tile, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(typed.ifEmpty { "—" }, color = if (typed.isEmpty()) QuizColors.Muted else QuizColors.OnDark, fontSize = 26.sp)
    }
    Spacer(Modifier.height(6.dp))
    val rows = listOf(listOf("1", "2", "3", "4", "5", "6"), listOf("7", "8", "9", "0", "A", "B"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in row) {
                    Key(key, Modifier.weight(1f), enabled = typed.length < 3) { onChange(typed + key) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameButton(stringResource(R.string.type_skip), onSkip, Modifier.weight(1f), fontSize = 18.sp)
            Key("⌫", Modifier.weight(1f), enabled = typed.isNotEmpty()) { onChange(typed.dropLast(1)) }
            GameButton(stringResource(R.string.type_validate), onSubmit, Modifier.weight(1.5f), enabled = typed.isNotBlank())
        }
    }
}

@Composable
private fun Key(text: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .background(QuizColors.Tile.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = QuizColors.OnDark.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 24.sp, textAlign = TextAlign.Center)
    }
}

/** Le lieu à nommer est en jaune ; pendant le retour, vert si juste, rouge sinon. */
private fun typeStyle(shapeId: String, state: GameUiState, inLevel: Set<String>, data: MapData, capitals: Set<String>): ShapeStyle? {
    val id = ownerOf(data, state.category, shapeId)
    val answer = state.lastAnswer.takeIf { state.phase == QuizSession.Phase.FEEDBACK }
    val isTarget = id == (answer?.targetId ?: state.targetId)
    val stateColor = when {
        isTarget && answer != null -> if (answer.correct) QuizColors.Correct else QuizColors.Wrong
        isTarget -> QuizColors.Accent
        else -> null
    }
    // En mode Saisir, la carte sert seulement à montrer le lieu : rien n'y est touchable.
    return gameStyle(shapeId, data, id, inLevel, stateColor, emphasized = isTarget, capitals = capitals, state.category)?.copy(tappable = false)
}
