package io.github.saksa84.francemapquiz.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.data.FeatureType
import io.github.saksa84.francemapquiz.data.MapData
import io.github.saksa84.francemapquiz.game.GeoCategory
import io.github.saksa84.francemapquiz.game.QuestionType
import io.github.saksa84.francemapquiz.ui.GameUiState
import io.github.saksa84.francemapquiz.map.MapBounds
import io.github.saksa84.francemapquiz.map.ShapeKind
import io.github.saksa84.francemapquiz.ui.map.ShapeStyle
import io.github.saksa84.francemapquiz.ui.common.BackButton
import io.github.saksa84.francemapquiz.ui.common.CompactEntityInfo
import io.github.saksa84.francemapquiz.ui.common.QuizBackground
import io.github.saksa84.francemapquiz.ui.common.QuizColors
import io.github.saksa84.francemapquiz.ui.common.formatDuration
import kotlinx.coroutines.delay

/**
 * Mise en page commune aux modes de jeu. La carte garde toujours la même taille : le texte de
 * retour a une hauteur réservée et la fiche de réponse s'affiche par-dessus le haut de la carte,
 * au-dessus de la Manche, pour ne pas cacher les cartouches de l'outre-mer en bas.
 * En paysage, tout passe dans une colonne à droite de la carte.
 */
@Composable
fun GameLayout(
    topBar: @Composable () -> Unit,
    feedback: @Composable () -> Unit,
    map: @Composable (Modifier) -> Unit,
    overlay: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    QuizBackground {
        // imePadding : en mode Saisir, le bas de l'écran remonte au-dessus du clavier.
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    map(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(360.dp).fillMaxHeight().padding(8.dp)) {
                        topBar()
                        feedback()
                        Spacer(Modifier.weight(1f))
                        overlay()
                        bottom()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        topBar()
                        feedback()
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        map(Modifier.fillMaxSize())
                        Column(Modifier.align(Alignment.TopCenter).padding(8.dp), content = overlay)
                    }
                    Column(Modifier.padding(8.dp), content = bottom)
                }
            }
        }
    }
}

/**
 * Lieu du quiz auquel appartient une forme de la carte : le département lui-même, ou sa région
 * dans la catégorie Régions (une région est l'union de ses départements).
 */
fun ownerOf(data: MapData, category: GeoCategory, shapeId: String): String =
    if (category == GeoCategory.REGIONS) data.catalog.regionOf(shapeId)?.id ?: shapeId else shapeId

/** Formes à mettre en évidence pour un lieu : ses départements pour une région, sinon lui-même. */
fun shapesOf(data: MapData, id: String?): Set<String> {
    val entity = id?.let { data.catalog[it] } ?: return emptySet()
    return if (entity.type == FeatureType.REGION) data.catalog.departmentsOf(entity.id).map { it.id }.toSet() else setOf(entity.id)
}

/**
 * Zone sur laquelle cadrer la carte au début d'un niveau : l'ensemble de ses départements
 * (la région « Occitanie », les cartouches de l'outre-mer…). On peut toujours dézoomer.
 */
fun levelFocus(data: MapData, targetIds: Collection<String>): MapBounds? {
    val bounds = targetIds.flatMap { shapesOf(data, it) }.mapNotNull { data.geometry[it]?.bounds }
    if (bounds.isEmpty()) return null
    return MapBounds(bounds.minOf { it.minX }, bounds.minOf { it.minY }, bounds.maxOf { it.maxX }, bounds.maxOf { it.maxY })
}

/**
 * Chefs-lieux à dessiner pendant une partie : seulement dans les questions « chef-lieu », ceux des
 * lieux du niveau (pour une région, son chef-lieu de région). Tous ont le même aspect : un point ne
 * doit jamais trahir la réponse.
 */
fun capitalsShown(data: MapData, question: QuestionType, targetIds: Collection<String>): Set<String> =
    if (question != QuestionType.CAPITAL) emptySet() else targetIds.mapNotNull { data.catalog[it]?.capitalId }.toSet()

/**
 * Apparence d'un département pendant un quiz, commune aux modes Trouver, Saisir et Éliminer.
 * Tous les lieux du niveau ont le même aspect au repos (aucun indice) ; les autres sont grisés et
 * non touchables. Les chefs-lieux de [capitals] sont de simples repères, non touchables.
 *
 * @param owner lieu du quiz auquel appartient la forme (voir [ownerOf])
 * @param state couleur d'état (sélection, bonne ou mauvaise réponse) ; null au repos
 */
fun gameStyle(
    shapeId: String,
    data: MapData,
    owner: String,
    inLevel: Set<String>,
    state: Color?,
    emphasized: Boolean,
    capitals: Set<String> = emptySet(),
): ShapeStyle? {
    val shape = data.geometry[shapeId] ?: return null
    if (shape.kind == ShapeKind.POINT) {
        return if (shapeId in capitals) ShapeStyle(QuizColors.Capital, tappable = false, largePoint = false) else null
    }
    if (shape.kind != ShapeKind.POLYGON) return null
    val playable = owner in inLevel
    return ShapeStyle(
        fill = state ?: if (playable) QuizColors.Land else QuizColors.LandOutOfLevel,
        smallMarker = playable,
        emphasized = emphasized,
        tappable = playable,
    )
}

/** Temps écoulé relu dix fois par seconde (le moteur le fige lui-même en fin de partie). */
@Composable
fun rememberElapsed(elapsedMs: () -> Long): Long {
    var shown by remember { mutableLongStateOf(elapsedMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            shown = elapsedMs()
            delay(100)
        }
    }
    return shown
}

/**
 * Bouton retour, chronomètre à gauche ; deux lignes d'indicateurs à droite (points, tour…).
 * @param paused chrono en pause (fiche ouverte) : signalé sous le temps
 */
@Composable
fun GameTopBar(elapsedMs: Long, firstLine: String, secondLine: String, onBack: () -> Unit, paused: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack)
        Column(Modifier.padding(start = 12.dp)) {
            Text(formatDuration(elapsedMs), color = QuizColors.OnDark, fontSize = 24.sp)
            if (paused) Text(stringResource(R.string.timer_paused), color = QuizColors.Accent, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(firstLine, color = QuizColors.OnDark, fontSize = 20.sp)
            Text(secondLine, color = QuizColors.OnDark)
        }
    }
}

/** Zone de hauteur fixe pour « Correct ! » / « Faux ! … » (vide quand rien à dire). */
@Composable
fun FeedbackText(text: String?, color: Color) {
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        if (text != null) {
            Text(text, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Fiche du lieu qu'il fallait trouver, affichée pendant le retour. « En savoir plus » la déplie ;
 * elle défile alors, pour tout lire sans cacher le bas de l'écran.
 */
@Composable
fun AnswerCard(data: MapData, entityId: String?, factsExpanded: Boolean, onFactsExpandedChange: (Boolean) -> Unit) {
    val entity = entityId?.let { data.catalog[it] } ?: return
    CompactEntityInfo(
        data.catalog,
        entity,
        data.facts[entity.id],
        Modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp)
            .background(QuizColors.Tile.copy(alpha = if (factsExpanded) 0.97f else 0.9f), RoundedCornerShape(6.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        color = QuizColors.OnDark,
        factsExpanded = factsExpanded,
        onFactsExpandedChange = onFactsExpandedChange,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun PromptText(text: String) {
    Text(
        text,
        color = QuizColors.Accent,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Consigne avec, en dessous, ce qu'elle désigne quand ce n'est pas un nom (« numéro », « chef-lieu »). */
@Composable
fun PromptWithCaption(text: String, caption: String?) {
    PromptText(text)
    if (caption != null) {
        Text(caption, color = QuizColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Sous-titre d'une consigne de Trouver ou Éliminer : null pour un nom, qui se suffit à lui-même. */
@Composable
fun promptCaption(state: GameUiState): String? = when (state.question) {
    QuestionType.NAME -> null
    QuestionType.CODE -> stringResource(R.string.prompt_caption_code)
    QuestionType.CAPITAL -> stringResource(
        if (state.category == GeoCategory.REGIONS) R.string.prompt_caption_region_capital else R.string.prompt_caption_capital,
    )
}

/** Valeur montrée dans la consigne (« Cantal », « 15 », « Aurillac »). */
fun promptValue(data: MapData, state: GameUiState): String =
    state.targetId?.let { data.catalog[it] }?.let { state.question.valueOf(it, data.catalog) } ?: ""

@Composable
fun GameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true, fontSize: TextUnit = 22.sp) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = QuizColors.Tile,
            contentColor = QuizColors.OnDark,
            disabledContainerColor = QuizColors.Tile.copy(alpha = 0.4f),
            disabledContentColor = QuizColors.OnDark.copy(alpha = 0.4f),
        ),
        modifier = modifier.height(56.dp),
    ) { Text(text, fontSize = fontSize, maxLines = 1) }
}

@Composable
fun GameProgress(done: Int, total: Int) {
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else done.toFloat() / total },
        modifier = Modifier.fillMaxWidth(),
        color = QuizColors.OnDark,
        trackColor = QuizColors.Tile,
    )
}
