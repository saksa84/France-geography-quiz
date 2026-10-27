package io.github.saksa84.francemapquiz.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.data.GeoCatalog
import io.github.saksa84.francemapquiz.game.AnswerRecord
import io.github.saksa84.francemapquiz.game.QuestionType
import io.github.saksa84.francemapquiz.game.QuizSummary
import io.github.saksa84.francemapquiz.ui.GameMode
import io.github.saksa84.francemapquiz.ui.common.QuizBackground
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.common.ScreenHeader
import io.github.saksa84.francemapquiz.ui.common.formatDuration

/** Fin de partie : score, pourcentage, temps et liste des erreurs, avec « rejouer les erreurs ». */
@Composable
fun ResultScreen(
    mode: GameMode,
    question: QuestionType,
    summary: QuizSummary,
    newRecord: Boolean,
    catalog: GeoCatalog,
    onReplayMistakes: () -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
) {
    QuizBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.result_title), onBack)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                        val big = 22.sp
                        val scoreLabel = if (mode == GameMode.ELIMINATE) R.string.result_first_try else R.string.result_score
                        Text(stringResource(scoreLabel, summary.correct, summary.total), color = QuizColors.OnDark, fontSize = big)
                        Text(stringResource(R.string.result_percent, summary.percent), color = QuizColors.OnDark, fontSize = big)
                        if (mode == GameMode.ELIMINATE) {
                            Text(stringResource(R.string.eliminate_errors, summary.mistakes.size), color = QuizColors.OnDark, fontSize = big)
                        }
                        Text(stringResource(R.string.result_time, formatDuration(summary.durationMs)), color = QuizColors.OnDark, fontSize = big)
                        if (newRecord) {
                            Text(
                                stringResource(R.string.result_new_record),
                                color = QuizColors.Accent,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
                item {
                    Text(
                        stringResource(if (summary.mistakes.isEmpty()) R.string.result_no_mistakes else R.string.result_mistakes_title),
                        color = if (summary.mistakes.isEmpty()) QuizColors.Correct else QuizColors.Accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // Une ligne par lieu manqué, même s'il y a eu plusieurs erreurs dessus (mode Éliminer).
                items(summary.mistakes.groupBy { it.targetId }.values.toList()) { tries -> MistakeRow(tries, catalog, question) }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val missed = summary.missedTargetIds.size
                if (missed > 0) GameButton(stringResource(R.string.result_replay_mistakes, missed), onReplayMistakes)
                GameButton(stringResource(R.string.result_replay), onReplay)
            }
        }
    }
}

/**
 * Un lieu manqué, et tout ce qui a été touché ou tapé à sa place. Pour une question sur le
 * chef-lieu, on rappelle le chef-lieu : c'est lui qu'il fallait retenir.
 */
@Composable
private fun MistakeRow(tries: List<AnswerRecord>, catalog: GeoCatalog, question: QuestionType) {
    fun name(id: String?) = id?.let { catalog[it]?.displayName } ?: "?"
    val first = tries.first()
    val target = catalog[first.targetId]
    val selected = tries.mapNotNull { it.selectedId }.distinct()
    val detail = when {
        selected.isNotEmpty() -> stringResource(R.string.result_you_selected, selected.joinToString(", ") { name(it) })
        first.typed != null -> stringResource(R.string.result_you_typed, first.typed)
        else -> stringResource(R.string.result_skipped)
    }
    val capital = target?.let { catalog.capitalOf(it)?.name }?.takeIf { question == QuestionType.CAPITAL }
    Column(
        Modifier
            .fillMaxWidth()
            .background(QuizColors.Tile, RoundedCornerShape(4.dp))
            .padding(12.dp),
    ) {
        Text(name(first.targetId), color = QuizColors.OnDark, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        if (capital != null) Text(stringResource(R.string.info_capital, capital), color = QuizColors.OnDark)
        Text(detail, color = QuizColors.Wrong)
    }
}
