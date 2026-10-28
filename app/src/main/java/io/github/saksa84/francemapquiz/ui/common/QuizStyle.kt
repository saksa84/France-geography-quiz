package io.github.saksa84.francemapquiz.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette des écrans de quiz : inspirée des applications de quiz géographique, pas copiée. */
object QuizColors {
    val BackgroundTop = Color(0xFF0E4F57)
    val BackgroundBottom = Color(0xFF138C93)
    val Tile = Color(0xFF0A3B41)
    val OnDark = Color(0xFFF2F5F5)
    val Muted = Color(0xFFB5CACC)
    val Accent = Color(0xFFFFC94D) // consigne, sélection en attente
    val Correct = Color(0xFF3CC56E)
    val Wrong = Color(0xFFE5534B)

    // Carte en mode jeu : terres grises neutres (aucun indice) et frontières blanches nettes.
    val Sea = Color(0xFF1D5E66)
    val Land = Color(0xFFC9CFCF)
    val Highlight = Color.White
    val LandOutOfLevel = Color(0xFF6C8487)
    val Eliminated = Land.copy(alpha = 0.22f) // mode Éliminer : département trouvé, presque effacé

    // Pays voisins, dans le cadre de la métropole : un peu plus clairs que la mer, sans attirer l'œil.
    val Context = Color(0xFF2E6E75)
    val Capital = Color(0xFF263033)

    // Cours d'eau : bleu vif dans le niveau, bleu gris effacé hors niveau.
    val River = Color(0xFF1E6FD9)
    val RiverOutOfLevel = Color(0xFF8FA6B8)

    // Montagnes : massifs ocre (tous pareils dans un niveau), sommets en triangles brun foncé.
    val Massif = Color(0xFFC9A26B)
    val MassifBackground = Color(0xFFD9C3A0).copy(alpha = 0.7f) // laisse voir les limites des départements
    val Peak = Color(0xFF6B3E1F)
    val PeakOutOfLevel = Color(0xFF9C8C7E)
    val Point = Color.White
    val Border = Color.White
}

/** Fond dégradé, contenu tenu à l'écart des barres système. */
@Composable
fun QuizBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(QuizColors.BackgroundTop, QuizColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        content = content,
    )
}

/** Titre d'écran avec un bouton retour. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) BackButton(onBack) else Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = QuizColors.OnDark,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .background(QuizColors.Tile.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = QuizColors.OnDark, fontSize = 32.sp)
    }
}

/**
 * Tuile carrée des menus (modes, niveaux).
 * @param record ligne de meilleur résultat (« 12 / 15 · 0:45.2 »), si le niveau a déjà été joué
 * @param progress barre de progression en bas de la tuile (0 à 1), ou null pour ne pas l'afficher
 * @param perfect étoile en haut à droite : niveau réussi sans faute
 */
@Composable
fun MenuTile(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    record: String? = null,
    progress: Float? = null,
    perfect: Boolean = false,
) {
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(if (enabled) 1f else 0.45f)
            .background(QuizColors.Tile, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
    ) {
        if (perfect) {
            Text("★", color = QuizColors.Accent, fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd))
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (record != null) {
                Text(record, color = QuizColors.OnDark, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.size(6.dp))
            }
            // Noms longs (« Auvergne-Rhône-Alpes ») : un peu plus petits, et coupés après un trait d'union plutôt qu'au milieu d'un mot.
            Text(
                title.replace("-", "-\u200B"),
                color = QuizColors.OnDark,
                fontSize = if (title.length > 14) 19.sp else 22.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(subtitle, color = QuizColors.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
        if (progress != null) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.85f),
                    color = QuizColors.OnDark,
                    trackColor = QuizColors.Muted.copy(alpha = 0.35f),
                    drawStopIndicator = {},
                )
                Text("${(progress * 100).toInt()} %", color = QuizColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

/** 83 456 ms -> « 1:23.4 ». */
fun formatDuration(ms: Long): String {
    val tenths = ms / 100
    return "%d:%02d.%d".format(tenths / 600, (tenths / 10) % 60, tenths % 10)
}
