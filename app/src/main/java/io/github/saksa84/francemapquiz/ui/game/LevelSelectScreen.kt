package io.github.saksa84.francemapquiz.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.game.QuestionType
import io.github.saksa84.francemapquiz.game.QuizLevel
import io.github.saksa84.francemapquiz.progress.Progress
import io.github.saksa84.francemapquiz.ui.GameMode
import io.github.saksa84.francemapquiz.ui.common.MenuTile
import io.github.saksa84.francemapquiz.ui.common.QuizBackground
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.common.ScreenHeader
import io.github.saksa84.francemapquiz.ui.common.formatDuration
import io.github.saksa84.francemapquiz.ui.common.questionName

/**
 * Choix de ce qu'on demande (nom, numéro, chef-lieu) puis du niveau, rangé par section
 * (par région, par grande zone, par numéro, toute la France). Commun aux modes de quiz.
 */
@Composable
fun LevelSelectScreen(
    mode: GameMode,
    question: QuestionType,
    questionTypes: List<QuestionType>,
    /** Clé de progression du mode et de la question (voir GeoCategory.progressKey). */
    progressKey: String,
    levels: List<QuizLevel>,
    progress: Progress,
    onQuestion: (QuestionType) -> Unit,
    onStart: (QuizLevel) -> Unit,
    onBack: () -> Unit,
) {
    QuizBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.levels_title, stringResource(modeName(mode))), onBack)
            if (questionTypes.size > 1) {
                Text(
                    stringResource(R.string.levels_question),
                    color = QuizColors.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (type in questionTypes) {
                        FilterChip(
                            selected = type == question,
                            onClick = { onQuestion(type) },
                            label = { Text(stringResource(questionName(type)), fontSize = 16.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = QuizColors.Tile,
                                labelColor = QuizColors.OnDark,
                                selectedContainerColor = QuizColors.Accent,
                                selectedLabelColor = QuizColors.Tile,
                            ),
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((section, sectionLevels) in levels.groupBy { it.section }) {
                    item(key = "section/$section", span = { GridItemSpan(maxLineSpan) }) {
                        Text(section, color = QuizColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(sectionLevels, key = { it.id }) { level ->
                        val count = pluralStringResource(R.plurals.question_count, level.targetIds.size, level.targetIds.size)
                        val best = progress.bestFor(progressKey, level.id)
                        MenuTile(
                            title = level.name,
                            subtitle = listOfNotNull(level.subtitle, count).joinToString(" · "),
                            onClick = { onStart(level) },
                            record = best?.let { "${it.correct} / ${it.total} · ${formatDuration(it.timeMs)}" },
                            progress = best?.fraction ?: 0f,
                            perfect = best?.perfect == true,
                        )
                    }
                }
            }
        }
    }
}

fun modeName(mode: GameMode): Int = when (mode) {
    GameMode.FIND -> R.string.mode_find
    GameMode.TYPE -> R.string.mode_type
    GameMode.ELIMINATE -> R.string.mode_eliminate
}
