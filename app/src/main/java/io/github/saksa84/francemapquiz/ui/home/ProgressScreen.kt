package io.github.saksa84.francemapquiz.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import io.github.saksa84.francemapquiz.data.Category
import io.github.saksa84.francemapquiz.data.GeoCatalog
import io.github.saksa84.francemapquiz.game.CategoryLevels
import io.github.saksa84.francemapquiz.game.GeoCategory
import io.github.saksa84.francemapquiz.game.QuestionType
import io.github.saksa84.francemapquiz.progress.Progress
import io.github.saksa84.francemapquiz.ui.GameMode
import io.github.saksa84.francemapquiz.ui.common.QuizBackground
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.common.ScreenHeader
import io.github.saksa84.francemapquiz.ui.common.formatDuration
import io.github.saksa84.francemapquiz.ui.common.questionName
import io.github.saksa84.francemapquiz.ui.game.GameButton
import io.github.saksa84.francemapquiz.ui.game.modeName

/** Combien d'erreurs fréquentes afficher et proposer en révision, par groupe. */
const val REVIEW_SIZE = 10

/** Erreur fréquente lue dans la progression : tel lieu, pour tel type de question. */
private data class Miss(val category: GeoCategory, val question: QuestionType, val id: String, val count: Int)

/**
 * Progression : pour chaque catégorie, pourcentage par mode et type de question, et meilleurs
 * résultats des niveaux déjà joués ; puis les erreurs les plus fréquentes, à réviser par groupe
 * (« les numéros que je rate le plus »).
 */
@Composable
fun ProgressScreen(
    catalog: GeoCatalog,
    levels: Map<GeoCategory, CategoryLevels>,
    progress: Progress,
    onReview: (GeoCategory, QuestionType, List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val misses = progress.missCounts.mapNotNull { (key, count) ->
        val (questionName, id) = Progress.parseMissKey(key) ?: return@mapNotNull null
        val question = QuestionType.entries.firstOrNull { it.name == questionName } ?: return@mapNotNull null
        val category = when (catalog[id]?.category) {
            Category.DEPARTMENT -> GeoCategory.DEPARTMENTS
            Category.REGION -> GeoCategory.REGIONS
            else -> return@mapNotNull null
        }
        Miss(category, question, id, count)
    }
    val groups = misses.groupBy { it.category to it.question }
        .mapValues { (_, list) -> list.sortedWith(compareByDescending<Miss> { it.count }.thenBy { it.id }).take(REVIEW_SIZE) }
        .toSortedMap(compareBy({ it.first.ordinal }, { it.second.ordinal }))

    QuizBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.progress_title), onBack)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Text(stringResource(R.string.progress_games, progress.gamesPlayed), color = QuizColors.Muted) }

                for (category in GeoCategory.entries) {
                    val categoryLevels = levels[category] ?: continue
                    val sizes = categoryLevels.levels.map { it.id to it.targetIds.size }
                    item(key = "title/${category.name}") { SectionTitle(stringResource(categoryName(category))) }
                    for (mode in listOf(GameMode.FIND, GameMode.ELIMINATE, GameMode.TYPE)) {
                        for (question in categoryLevels.questionTypes) {
                            val key = category.progressKey(mode.name, question)
                            item(key = "mode/$key") {
                                val percent = (progress.completion(key, sizes) * 100).toInt()
                                Text(
                                    "${stringResource(modeName(mode))} · ${stringResource(questionName(question))} · $percent %",
                                    color = QuizColors.OnDark,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            // Seuls les niveaux déjà joués : la liste reste courte.
                            val played = categoryLevels.levels.mapNotNull { level -> progress.bestFor(key, level.id)?.let { level to it } }
                            items(played, key = { (level, _) -> "$key/${level.id}" }) { (level, best) ->
                                Row(
                                    Modifier.fillMaxWidth().background(QuizColors.Tile, RoundedCornerShape(4.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(level.name, color = QuizColors.OnDark, modifier = Modifier.weight(1f))
                                    Text(
                                        "${if (best.perfect) "★ " else ""}${best.correct} / ${best.total} · ${formatDuration(best.timeMs)}",
                                        color = if (best.perfect) QuizColors.Accent else QuizColors.OnDark,
                                    )
                                }
                            }
                        }
                    }
                }

                item { SectionTitle(stringResource(R.string.progress_most_missed)) }
                if (groups.isEmpty()) {
                    item { Text(stringResource(R.string.progress_no_missed), color = QuizColors.Muted) }
                }
                for ((group, list) in groups) {
                    val (category, question) = group
                    item(key = "missed-title/${category.name}/${question.name}") {
                        Text(
                            "${stringResource(categoryName(category))} · ${stringResource(questionName(question))}",
                            color = QuizColors.OnDark,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(list, key = { "missed/${it.question}/${it.id}" }) { miss ->
                        Row(
                            Modifier.fillMaxWidth().background(QuizColors.Tile, RoundedCornerShape(4.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val entity = catalog[miss.id]
                            val value = entity?.let { question.valueOf(it, catalog) }.takeIf { question == QuestionType.CAPITAL }
                            Text(
                                listOfNotNull(entity?.displayName ?: miss.id, value).joinToString(" · "),
                                color = QuizColors.OnDark, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            Text(stringResource(R.string.progress_missed_count, miss.count), color = QuizColors.Wrong)
                        }
                    }
                    item(key = "missed-review/${category.name}/${question.name}") {
                        GameButton(stringResource(R.string.progress_review, list.size), { onReview(category, question, list.map { it.id }) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = QuizColors.Accent,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
