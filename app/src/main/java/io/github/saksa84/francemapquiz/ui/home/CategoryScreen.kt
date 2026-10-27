package io.github.saksa84.francemapquiz.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.game.CategoryLevels
import io.github.saksa84.francemapquiz.game.GeoCategory
import io.github.saksa84.francemapquiz.progress.Progress
import io.github.saksa84.francemapquiz.ui.GameMode
import io.github.saksa84.francemapquiz.ui.common.MenuTile
import io.github.saksa84.francemapquiz.ui.common.QuizBackground
import io.github.saksa84.francemapquiz.ui.common.ScreenHeader

/**
 * Modes d'une catégorie, avec la progression de chacun (moyenne sur les types de question).
 * Les modes pas encore développés sont grisés.
 */
@Composable
fun CategoryScreen(
    category: GeoCategory,
    levels: CategoryLevels?,
    progress: Progress,
    onLearn: () -> Unit,
    onMode: (GameMode) -> Unit,
    onBack: () -> Unit,
) {
    val sizes = levels?.levels.orEmpty().map { it.id to it.targetIds.size }
    val questions = levels?.questionTypes.orEmpty()
    fun completion(mode: GameMode): Float =
        if (questions.isEmpty()) 0f else questions.sumOf { progress.completion(category.progressKey(mode.name, it), sizes).toDouble() }.toFloat() / questions.size

    QuizBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(categoryName(category)), onBack)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { MenuTile(stringResource(R.string.mode_learn), stringResource(R.string.mode_learn_sub), onLearn) }
                item {
                    MenuTile(stringResource(R.string.mode_find), stringResource(R.string.mode_find_sub), { onMode(GameMode.FIND) }, progress = completion(GameMode.FIND))
                }
                item {
                    MenuTile(stringResource(R.string.mode_eliminate), stringResource(R.string.mode_eliminate_sub), { onMode(GameMode.ELIMINATE) }, progress = completion(GameMode.ELIMINATE))
                }
                item {
                    MenuTile(stringResource(R.string.mode_type), stringResource(R.string.mode_type_sub), { onMode(GameMode.TYPE) }, progress = completion(GameMode.TYPE))
                }
                item { MenuTile(stringResource(R.string.mode_outlines), stringResource(R.string.mode_soon), {}, enabled = false) }
            }
        }
    }
}
