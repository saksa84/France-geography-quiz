package io.github.saksa84.francemapquiz.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.ui.common.QuizBackground
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.game.EliminateScreen
import io.github.saksa84.francemapquiz.ui.game.FindScreen
import io.github.saksa84.francemapquiz.ui.game.LevelSelectScreen
import io.github.saksa84.francemapquiz.ui.game.ResultScreen
import io.github.saksa84.francemapquiz.ui.game.TypeScreen
import io.github.saksa84.francemapquiz.ui.home.CategoryScreen
import io.github.saksa84.francemapquiz.ui.home.HomeScreen
import io.github.saksa84.francemapquiz.ui.home.ProgressScreen
import io.github.saksa84.francemapquiz.ui.home.SettingsScreen
import io.github.saksa84.francemapquiz.ui.learn.LearnScreen

/**
 * Racine de l'application : affiche l'écran courant du [AppViewModel].
 * Navigation volontairement simple (une variable d'état), comme dans China Map Quiz.
 */
@Composable
fun FranceMapQuizApp(vm: AppViewModel = viewModel()) {
    val activity = LocalContext.current as? Activity
    // Bouton retour du téléphone : écran précédent, ou fermeture depuis l'accueil.
    BackHandler { if (!vm.back()) activity?.finish() }

    // Icônes de la barre d'état (heure, batterie) : foncées sur l'écran Apprendre en thème clair,
    // claires partout ailleurs (écrans de quiz sur fond sombre).
    val view = LocalView.current
    val lightBackground = vm.screen == Screen.LEARN && !isSystemInDarkTheme()
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBackground
                isAppearanceLightNavigationBars = lightBackground
            }
        }
    }

    when (val load = vm.loadState) {
        LoadState.Loading -> QuizBackground {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = QuizColors.OnDark)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.loading), color = QuizColors.OnDark)
            }
        }
        is LoadState.Failed -> QuizBackground {
            Text(
                stringResource(R.string.loading_error, load.message),
                color = QuizColors.Wrong,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        is LoadState.Ready -> when (vm.screen) {
            Screen.HOME -> HomeScreen(
                levels = load.levels,
                progress = vm.progress,
                onCategory = vm::chooseCategory,
                onProgress = { vm.open(Screen.PROGRESS) },
                onSettings = { vm.open(Screen.SETTINGS) },
            )
            Screen.CATEGORY -> CategoryScreen(
                category = vm.category,
                levels = load.levels[vm.category],
                progress = vm.progress,
                onLearn = { vm.open(Screen.LEARN) },
                onMode = vm::chooseMode,
                onBack = { vm.back() },
            )
            Screen.LEARN -> LearnScreen(load.data, vm.category, onBack = { vm.back() })
            Screen.LEVELS -> LevelSelectScreen(
                mode = vm.mode,
                question = vm.question,
                questionTypes = load.levels[vm.category]?.questionTypes.orEmpty(),
                progressKey = vm.progressKey,
                levels = load.levels[vm.category]?.levels.orEmpty(),
                progress = vm.progress,
                onQuestion = vm::chooseQuestion,
                onStart = vm::startLevel,
                onBack = { vm.back() },
            )
            Screen.PROGRESS -> ProgressScreen(
                catalog = load.data.catalog,
                levels = load.levels,
                progress = vm.progress,
                onReview = vm::review,
                onBack = { vm.back() },
            )
            Screen.SETTINGS -> SettingsScreen(
                settings = vm.settings,
                onAnswerSounds = vm::setAnswerSounds,
                onResetProgress = vm::resetProgress,
                onBack = { vm.back() },
            )
            Screen.GAME -> vm.game?.let { state ->
                when (state.mode) {
                    GameMode.FIND -> FindScreen(
                        data = load.data,
                        state = state,
                        elapsedMs = vm::elapsedMs,
                        onTap = vm::tap,
                        onConfirm = vm::confirm,
                        onNext = vm::next,
                        onFactsExpanded = vm::setFactsExpanded,
                        onBack = { vm.back() },
                    )
                    GameMode.TYPE -> TypeScreen(
                        data = load.data,
                        state = state,
                        elapsedMs = vm::elapsedMs,
                        onSubmit = vm::submitTyped,
                        onSkip = vm::skip,
                        onNext = vm::next,
                        onFactsExpanded = vm::setFactsExpanded,
                        onBack = { vm.back() },
                    )
                    GameMode.ELIMINATE -> EliminateScreen(
                        data = load.data,
                        state = state,
                        elapsedMs = vm::elapsedMs,
                        onTap = vm::tap,
                        onConfirm = vm::confirm,
                        onBack = { vm.back() },
                    )
                }
                if (vm.confirmingQuit) QuitDialog(onQuit = vm::quitGame, onStay = vm::keepPlaying)
            }
            Screen.RESULT -> vm.summary?.let { summary ->
                ResultScreen(
                    mode = vm.mode,
                    question = vm.question,
                    summary = summary,
                    newRecord = vm.newRecord,
                    catalog = load.data.catalog,
                    onReplayMistakes = vm::replayMistakes,
                    onReplay = vm::replay,
                    onBack = { vm.back() },
                )
            }
        }
    }
}

@Composable
private fun QuitDialog(onQuit: () -> Unit, onStay: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.quit_title)) },
        text = { Text(stringResource(R.string.quit_message)) },
        confirmButton = { TextButton(onClick = onQuit) { Text(stringResource(R.string.quit_confirm)) } },
        dismissButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.quit_stay)) } },
    )
}
