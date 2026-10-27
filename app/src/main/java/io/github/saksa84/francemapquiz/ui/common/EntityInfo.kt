package io.github.saksa84.francemapquiz.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.data.FeatureType
import io.github.saksa84.francemapquiz.data.GeoCatalog
import io.github.saksa84.francemapquiz.data.GeoEntity
import io.github.saksa84.francemapquiz.data.PlaceFacts
import io.github.saksa84.francemapquiz.game.QuestionType
import java.util.Locale

/** Fiche d'un lieu (mode Apprendre) : nom, numéro, chef-lieu, région, superficie, puis « En savoir plus ». */
@Composable
fun EntityInfo(
    catalog: GeoCatalog,
    entity: GeoEntity,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    facts: PlaceFacts? = null,
    factsExpanded: Boolean = false,
    onFactsExpandedChange: (Boolean) -> Unit = {},
) {
    Column(modifier) {
        Text(entity.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Text(subtitle(catalog, entity), style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.height(8.dp))
        infoLines(catalog, entity, compact = false).forEach { Text(it, color = color) }
        entity.note?.let { NoteText(it, color) }
        if (facts != null) FactsSection(facts, factsExpanded, onFactsExpandedChange, color)
    }
}

/**
 * Fiche compacte du retour de réponse en jeu : 3 lignes, pour ne pas cacher la carte.
 * Nom et numéro, chef-lieu et région, superficie ; « En savoir plus » la déplie quand une fiche existe.
 */
@Composable
fun CompactEntityInfo(
    catalog: GeoCatalog,
    entity: GeoEntity,
    facts: PlaceFacts?,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    factsExpanded: Boolean = false,
    onFactsExpandedChange: ((Boolean) -> Unit)? = null,
) {
    Column(modifier) {
        Text(
            entity.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        infoLines(catalog, entity, compact = true).forEach {
            Text(it, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        entity.note?.let { NoteText(it, color) }
        if (facts != null && onFactsExpandedChange != null) FactsSection(facts, factsExpanded, onFactsExpandedChange, color)
    }
}

/** Les lignes d'information d'un lieu ; [compact] regroupe chef-lieu et région sur une ligne. */
@Composable
private fun infoLines(catalog: GeoCatalog, entity: GeoEntity, compact: Boolean): List<String> {
    val capital = catalog.capitalOf(entity)?.name
    return when (entity.type) {
        FeatureType.DEPARTMENT -> {
            val region = catalog.regionOf(entity.id)?.name
            val area = entity.areaKm2?.let { stringResource(R.string.info_area, String.format(Locale.FRENCH, "%,d", it)) }
            val population = populationText(entity)
            if (compact) {
                listOfNotNull(
                    listOfNotNull(capital?.let { stringResource(R.string.info_capital, it) }, region).joinToString(" · "),
                    listOfNotNull(population, area).joinToString(" · ").ifEmpty { null },
                )
            } else {
                listOfNotNull(
                    capital?.let { stringResource(R.string.info_capital, it) },
                    region?.let { stringResource(R.string.info_region, it) },
                    population,
                    area,
                    catalog.riversThrough(entity.id).joinToString(", ") { it.name }.ifEmpty { null }
                        ?.let { stringResource(R.string.info_rivers, it) },
                )
            }
        }
        FeatureType.REGION -> {
            val departments = catalog.departmentsOf(entity.id)
            val count = pluralStringResource(R.plurals.department_count, departments.size, departments.size)
            listOfNotNull(
                capital?.let { stringResource(R.string.info_region_capital, it) },
                populationText(entity),
                if (compact) count else "$count : " + departments.joinToString(", ") { it.displayName },
            )
        }
        FeatureType.PREFECTURE -> {
            val department = entity.parentId?.let { catalog[it] }
            listOfNotNull(department?.let { stringResource(R.string.info_capital_of, it.displayName) })
        }
        FeatureType.RIVER -> {
            val tributaries = catalog.tributariesOf(entity.id).joinToString(", ") { it.name }.ifEmpty { null }
            val mouth = catalog.mouthOf(entity).takeIf { it.id != entity.id && it.id != entity.parentId }
            val crosses = entity.departmentIds.mapNotNull { catalog[it]?.displayName }.joinToString(", ").ifEmpty { null }
            val named = catalog.departmentsNamedAfter(entity).joinToString(", ") { it.displayName }.ifEmpty { null }
            if (compact) {
                listOfNotNull(riverSubtitle(catalog, entity), tributaries?.let { stringResource(R.string.info_tributaries, it) })
            } else {
                listOfNotNull(
                    mouth?.let { stringResource(R.string.info_basin, it.name) },
                    tributaries?.let { stringResource(R.string.info_tributaries, it) },
                    crosses?.let { stringResource(R.string.info_crosses, it) },
                    named?.let { stringResource(R.string.info_named_after, it) },
                )
            }
        }
    }
}

/** Note administrative, en petit et en italique : un fait de statut, pas une information à retenir. */
@Composable
private fun NoteText(note: String, color: Color) {
    Text(note, color = color, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
}

/** « Population : 1 690 493 hab. (2023) » ; null si l'INSEE ne la donne pas (Mayotte). */
@Composable
private fun populationText(entity: GeoEntity): String? {
    val population = entity.population ?: return null
    val year = entity.populationYear ?: return null
    return stringResource(R.string.info_population, String.format(Locale.FRENCH, "%,d", population), year)
}

/** « Département n° 15 », « Région d'outre-mer »… */
@Composable
private fun subtitle(catalog: GeoCatalog, entity: GeoEntity): String = when (entity.type) {
    FeatureType.DEPARTMENT -> stringResource(
        if (entity.overseas) R.string.type_overseas_department_code else R.string.type_department_code,
        entity.code ?: "",
    )
    FeatureType.REGION -> stringResource(if (entity.overseas) R.string.type_overseas_region else R.string.type_region)
    FeatureType.PREFECTURE -> stringResource(R.string.type_prefecture)
    FeatureType.RIVER -> riverSubtitle(catalog, entity)
}

/** « Fleuve », ou « Rivière, affluent : se jette dans la Loire » (avec l'article, tiré des alias). */
@Composable
private fun riverSubtitle(catalog: GeoCatalog, entity: GeoEntity): String {
    val parent = entity.parentId?.let { catalog[it] } ?: return stringResource(R.string.type_river_mouth)
    return stringResource(R.string.type_river_tributary, parent.aliases.firstOrNull() ?: parent.name)
}

/**
 * « En savoir plus » : rubriques de la fiche, repliées par défaut pour garder la carte visible.
 * L'état est tenu par l'écran, qui agrandit le panneau quand la section est dépliée.
 */
@Composable
private fun FactsSection(facts: PlaceFacts, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, color: Color) {
    TextButton(onClick = { onExpandedChange(!expanded) }, contentPadding = PaddingValues(0.dp)) {
        // Sur le fond sombre des quiz, le bouton prend la couleur des consignes pour rester lisible.
        Text(
            stringResource(if (expanded) R.string.facts_less else R.string.facts_more),
            color = if (color == Color.Unspecified) Color.Unspecified else QuizColors.Accent,
        )
    }
    if (!expanded) return
    val lines = buildList {
        facts.nameOrigin?.let { add(stringResource(R.string.facts_name_origin, it)) }
        facts.history?.let { add(stringResource(R.string.facts_history, it)) }
        facts.legend?.let { add(stringResource(R.string.facts_legend, it)) }
        facts.anecdotes.forEach { add(stringResource(R.string.facts_anecdote, it)) }
    }
    lines.forEach { Text(it, color = color, modifier = Modifier.padding(bottom = 6.dp)) }
    if (!facts.reviewed) {
        Text(stringResource(R.string.facts_unreviewed), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = color)
    }
}

/** Nom court du type de question : « nom », « numéro », « chef-lieu ». */
@StringRes
fun questionName(question: QuestionType): Int = when (question) {
    QuestionType.NAME -> R.string.question_name
    QuestionType.CODE -> R.string.question_code
    QuestionType.CAPITAL -> R.string.question_capital
}
