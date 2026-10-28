package io.github.saksa84.francemapquiz.ui.home

import androidx.annotation.StringRes
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

/** Accueil : que veut-on apprendre ? Départements, régions, cours d'eau, montagnes ; puis progression et paramètres. */
@Composable
fun HomeScreen(
    levels: Map<GeoCategory, CategoryLevels>,
    progress: Progress,
    onCategory: (GeoCategory) -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
) {
    QuizBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.home_title), onBack = null)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (category in GeoCategory.entries) {
                    item {
                        MenuTile(
                            title = stringResource(categoryName(category)),
                            subtitle = stringResource(categoryDescription(category)),
                            onClick = { onCategory(category) },
                            progress = categoryCompletion(category, levels[category], progress),
                        )
                    }
                }
                item { MenuTile(stringResource(R.string.progress_title), stringResource(R.string.progress_sub), onProgress) }
                item { MenuTile(stringResource(R.string.settings_title), null, onSettings) }
            }
        }
    }
}

/** Progression d'une catégorie : moyenne sur ses trois modes et tous ses types de question. */
fun categoryCompletion(category: GeoCategory, levels: CategoryLevels?, progress: Progress): Float {
    if (levels == null || levels.questionTypes.isEmpty()) return 0f
    val sizes = levels.levels.map { it.id to it.targetIds.size }
    val keys = GameMode.entries.flatMap { mode -> levels.questionTypes.map { category.progressKey(mode.name, it) } }
    return keys.sumOf { progress.completion(it, sizes).toDouble() }.toFloat() / keys.size
}

@StringRes
fun categoryName(category: GeoCategory): Int = when (category) {
    GeoCategory.DEPARTMENTS -> R.string.category_departments
    GeoCategory.REGIONS -> R.string.category_regions
    GeoCategory.RIVERS -> R.string.category_rivers
    GeoCategory.MOUNTAINS -> R.string.category_mountains
}

@StringRes
private fun categoryDescription(category: GeoCategory): Int = when (category) {
    GeoCategory.DEPARTMENTS -> R.string.category_departments_sub
    GeoCategory.REGIONS -> R.string.category_regions_sub
    GeoCategory.RIVERS -> R.string.category_rivers_sub
    GeoCategory.MOUNTAINS -> R.string.category_mountains_sub
}
