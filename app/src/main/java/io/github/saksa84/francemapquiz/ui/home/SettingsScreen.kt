package io.github.saksa84.francemapquiz.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.progress.Settings
import io.github.saksa84.francemapquiz.ui.common.QuizBackground
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.common.ScreenHeader
import io.github.saksa84.francemapquiz.ui.game.GameButton

/** Paramètres : sons de réponse, remise à zéro de la progression, à propos et sources. */
@Composable
fun SettingsScreen(
    settings: Settings,
    onAnswerSounds: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmingReset by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    QuizBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.settings_title), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingSwitch(R.string.settings_answer_sounds, R.string.settings_answer_sounds_sub, settings.answerSounds, onAnswerSounds)

                GameButton(stringResource(R.string.settings_reset), { confirmingReset = true })

                Text(stringResource(R.string.about_title), color = QuizColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.about_text, version), color = QuizColors.OnDark)
                Text(stringResource(R.string.about_credits), color = QuizColors.OnDark)
                Text(stringResource(R.string.about_sources), color = QuizColors.Muted)
            }
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingReset = false
                    onResetProgress()
                }) { Text(stringResource(R.string.reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.reset_cancel)) } },
        )
    }
}

@Composable
private fun SettingSwitch(@StringRes title: Int, @StringRes subtitle: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(QuizColors.Tile, RoundedCornerShape(4.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = QuizColors.OnDark, fontSize = 18.sp)
            Text(stringResource(subtitle), color = QuizColors.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = QuizColors.Accent, checkedThumbColor = QuizColors.Tile),
        )
    }
}
