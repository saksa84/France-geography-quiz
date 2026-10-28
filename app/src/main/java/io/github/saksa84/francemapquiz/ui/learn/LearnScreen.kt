package io.github.saksa84.francemapquiz.ui.learn

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.saksa84.francemapquiz.R
import io.github.saksa84.francemapquiz.data.Category
import io.github.saksa84.francemapquiz.data.FeatureType
import io.github.saksa84.francemapquiz.data.GeoCatalog
import io.github.saksa84.francemapquiz.data.MapData
import io.github.saksa84.francemapquiz.data.PlaceFacts
import io.github.saksa84.francemapquiz.game.GeoCategory
import io.github.saksa84.francemapquiz.map.MapPoint
import io.github.saksa84.francemapquiz.map.ShapeKind
import io.github.saksa84.francemapquiz.ui.common.EntityInfo
import io.github.saksa84.francemapquiz.ui.home.categoryName
import io.github.saksa84.francemapquiz.ui.map.FranceMap
import io.github.saksa84.francemapquiz.ui.map.LabelStyle
import io.github.saksa84.francemapquiz.ui.map.MapLabel
import io.github.saksa84.francemapquiz.ui.map.ShapeStyle
import kotlin.math.sqrt

/** Ce qu'on écrit sur la carte en mode Apprendre. */
private enum class LabelKind { NONE, NAMES, CODES, CAPITALS }

/**
 * Mode Apprendre : explorer la carte sans chronomètre. Toucher un lieu affiche sa fiche.
 * Deux vues : départements, ou régions (toucher un département sélectionne toute sa région).
 * Dans les catégories Cours d'eau, Montagnes et Parcs nationaux, les départements sont un fond neutre et
 * l'on touche les fleuves et rivières, les massifs et les sommets, ou les parcs. La catégorie Outre-mer
 * ajoute les collectivités d'outre-mer (seconde rangée de cartouches).
 * On peut écrire sur la carte les noms, les numéros ou les chefs-lieux : seuls ceux qui ont la place
 * apparaissent, et il y en a de plus en plus quand on zoome.
 */
@Composable
fun LearnScreen(data: MapData, category: GeoCategory, onBack: () -> Unit) {
    // rememberSaveable : sélection et options survivent à la rotation de l'écran.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var regionView by rememberSaveable { mutableStateOf(category == GeoCategory.REGIONS) }
    var labelKind by rememberSaveable { mutableStateOf(LabelKind.NAMES) }
    var recenterKey by rememberSaveable { mutableIntStateOf(0) }
    // « En savoir plus » déplié : replié de nouveau dès qu'on touche un autre lieu.
    var factsExpanded by rememberSaveable(selectedId) { mutableStateOf(false) }
    val catalog = data.catalog

    val overseas = category == GeoCategory.OVERSEAS
    val departmentLabels = remember(data, overseas) { departmentLabels(data, overseas) }
    val codeLabels = remember(data, overseas) { codeLabels(data, overseas) }
    val capitalLabels = remember(data, overseas) { capitalLabels(data, regionCapitalsOnly = false, withCollectivities = overseas) }
    val regionCapitalLabels = remember(data) { capitalLabels(data, regionCapitalsOnly = true, withCollectivities = false) }
    val regionLabels = remember(data) { regionLabels(data) }
    // Cours d'eau d'abord (prioritaires en cas de chevauchement), puis les départements en fond, plus discrets.
    val riverLabels = remember(data) { riverLabels(data) + departmentLabels(data, false).map { it.copy(style = LabelStyle.BACKGROUND) } }
    val parks = category == GeoCategory.PARKS
    val parkLabels = remember(data) { parkLabels(data) + departmentLabels(data, false).map { it.copy(style = LabelStyle.BACKGROUND) } }
    val rivers = category == GeoCategory.RIVERS
    val mountains = category == GeoCategory.MOUNTAINS
    val mountainLabels = remember(data) { mountainLabels(data) }
    val regionCapitals = remember(data) { catalog.inCategory(Category.REGION).mapNotNull { it.capitalId }.toSet() }

    // En vue régions, le lieu sélectionné est une région ; ses départements sont mis en évidence.
    val highlighted: Set<String> = remember(selectedId) {
        val selected = selectedId?.let { catalog[it] }
        if (selected?.type == FeatureType.REGION) catalog.departmentsOf(selected.id).map { it.id }.toSet() else setOfNotNull(selectedId)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                Text(
                    stringResource(R.string.learn_title_category, stringResource(categoryName(category))),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { recenterKey++ }) { Text(stringResource(R.string.map_recenter)) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (category == GeoCategory.DEPARTMENTS) {
                    FilterChip(
                        selected = !regionView,
                        onClick = { regionView = false; selectedId = null },
                        label = { Text(stringResource(R.string.learn_view_departments)) },
                    )
                    FilterChip(
                        selected = regionView,
                        onClick = { regionView = true; selectedId = null },
                        label = { Text(stringResource(R.string.learn_view_regions)) },
                    )
                }
                // Un seul type d'étiquette à la fois ; toucher celui qui est choisi les retire.
                val kinds = when {
                    rivers || mountains || parks -> listOf(LabelKind.NAMES)
                    regionView -> listOf(LabelKind.NAMES, LabelKind.CAPITALS)
                    else -> listOf(LabelKind.NAMES, LabelKind.CODES, LabelKind.CAPITALS)
                }
                for (kind in kinds) {
                    FilterChip(
                        selected = labelKind == kind,
                        onClick = { labelKind = if (labelKind == kind) LabelKind.NONE else kind },
                        label = { Text(stringResource(labelName(kind))) },
                    )
                }
            }

            val map = @Composable { modifier: Modifier ->
                FranceMap(
                    geometry = data.geometry,
                    styleOf = { id ->
                        when {
                            rivers -> riverStyle(data, id, highlighted)
                            mountains -> mountainStyle(data, id, highlighted)
                            parks -> parkStyle(data, id, highlighted)
                            else -> learnStyle(data, id, highlighted, regionView, regionCapitals, overseas)
                        }
                    },
                    selectedIds = highlighted,
                    onShapeTap = { id ->
                        // Vue régions : un département touché sélectionne sa région.
                        selectedId = if (regionView && id != null) catalog.regionOf(id)?.id ?: id else id
                    },
                    recenterKey = recenterKey,
                    // Les cours d'eau sont tous en métropole : les cartouches de l'outre-mer resteraient vides.
                    focus = if (rivers) data.geometry.frame("metro")?.bounds else null,
                    labels = when (labelKind) {
                        LabelKind.NONE -> emptyList()
                        LabelKind.NAMES -> when {
                            rivers -> riverLabels
                            mountains -> mountainLabels
                            parks -> parkLabels
                            regionView -> regionLabels
                            else -> departmentLabels
                        }
                        LabelKind.CODES -> codeLabels
                        LabelKind.CAPITALS -> if (regionView) regionCapitalLabels else capitalLabels
                    },
                    modifier = modifier,
                )
            }
            // Taille fixe du panneau : la carte ne change pas de taille selon la quantité d'informations,
            // sauf quand on déplie « En savoir plus » : le panneau prend alors 60 % de la hauteur.
            // En paysage, le panneau passe à droite pour laisser toute la hauteur à la carte.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val hint = when {
                    rivers -> R.string.learn_hint_rivers
                    mountains -> R.string.learn_hint_mountains
                    parks -> R.string.learn_hint_parks
                    overseas -> R.string.learn_hint_overseas
                    regionView -> R.string.learn_hint_regions
                    else -> R.string.learn_hint
                }
                if (maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize()) {
                        map(Modifier.weight(1f).fillMaxHeight())
                        InfoPanel(catalog, data.facts, selectedId, hint, factsExpanded, { factsExpanded = it }, Modifier.width(340.dp).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        map(Modifier.weight(1f).fillMaxWidth())
                        InfoPanel(
                            catalog, data.facts, selectedId, hint, factsExpanded, { factsExpanded = it },
                            Modifier.fillMaxWidth().then(if (factsExpanded) Modifier.fillMaxHeight(0.6f) else Modifier.height(190.dp)),
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun labelName(kind: LabelKind): Int = when (kind) {
    LabelKind.NONE, LabelKind.NAMES -> R.string.learn_labels_names
    LabelKind.CODES -> R.string.learn_labels_codes
    LabelKind.CAPITALS -> R.string.learn_labels_capitals
}

@Composable
private fun InfoPanel(
    catalog: GeoCatalog,
    facts: Map<String, PlaceFacts>,
    selectedId: String?,
    @StringRes hint: Int,
    factsExpanded: Boolean,
    onFactsExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Surface(tonalElevation = 3.dp, modifier = modifier) {
        val entity = selectedId?.let { catalog[it] }
        // Défilement : la fiche d'une région (liste des départements) peut dépasser le panneau.
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (entity == null) {
                Text(stringResource(hint), modifier = Modifier.padding(16.dp))
            } else {
                EntityInfo(catalog, entity, Modifier.padding(16.dp), facts = facts[entity.id], factsExpanded = factsExpanded, onFactsExpandedChange = onFactsExpandedChange)
            }
        }
    }
}

/** Noms des départements, à leur point d'étiquette ; la place disponible vient de la distance au bord. */
private fun departmentLabels(data: MapData, withCollectivities: Boolean): List<MapLabel> = data.geometry.shapes.filter {
    val type = data.catalog[it.id]?.type
    type == FeatureType.DEPARTMENT || (withCollectivities && type == FeatureType.COLLECTIVITY)
}.mapNotNull { shape ->
    val name = data.catalog[shape.id]?.name ?: return@mapNotNull null
    // Le nom peut déborder un peu du cercle inscrit : il est bien plus large que haut, et les
    // chevauchements entre noms sont de toute façon évités au dessin.
    MapLabel(name, shape.label, fitWidth = shape.labelRoom * 4.5f)
}

/** Numéros des départements : courts, ils apparaissent presque tous dès la vue d'ensemble. */
private fun codeLabels(data: MapData, withCollectivities: Boolean): List<MapLabel> = data.geometry.shapes.filter {
    val type = data.catalog[it.id]?.type
    type == FeatureType.DEPARTMENT || (withCollectivities && type == FeatureType.COLLECTIVITY)
}.mapNotNull { shape ->
    val code = data.catalog[shape.id]?.code ?: return@mapNotNull null
    MapLabel(code, shape.label, fitWidth = shape.labelRoom * 4.5f)
}

/**
 * Chefs-lieux, écrits au-dessus de leur point. Les plus grands départements d'abord : en cas de
 * chevauchement, c'est le chef-lieu du plus grand qui garde son nom.
 */
private fun capitalLabels(data: MapData, regionCapitalsOnly: Boolean, withCollectivities: Boolean): List<MapLabel> {
    val regionCapitals = data.catalog.inCategory(Category.REGION).mapNotNull { it.capitalId }.toSet()
    return data.catalog.inCategory(Category.PREFECTURE).mapNotNull { capital ->
        if (regionCapitalsOnly && capital.id !in regionCapitals) return@mapNotNull null
        if (!withCollectivities && isCollectivityCapital(data, capital.id)) return@mapNotNull null
        val point = data.geometry[capital.id] ?: return@mapNotNull null
        val department = capital.parentId?.let { data.geometry[it] }
        MapLabel(capital.name, point.label, fitWidth = 1e6f + (department?.area ?: 0f) / 1e3f, aboveDp = 9f)
    }
}

/** Noms des régions, au barycentre de leurs départements pondéré par la surface. */
private fun regionLabels(data: MapData): List<MapLabel> {
    val catalog = data.catalog
    return catalog.inCategory(Category.REGION).mapNotNull { region ->
        val shapes = catalog.departmentsOf(region.id).mapNotNull { data.geometry[it.id] }
        val area = shapes.sumOf { it.area.toDouble() }.toFloat()
        if (shapes.isEmpty() || area == 0f) return@mapNotNull null
        val x = shapes.sumOf { (it.label.x * it.area).toDouble() }.toFloat() / area
        val y = shapes.sumOf { (it.label.y * it.area).toDouble() }.toFloat() / area
        MapLabel(region.name, MapPoint(x, y), fitWidth = sqrt(area))
    }
}

/** Noms des cours d'eau, au milieu de leur plus long tronçon ; les fleuves, listés d'abord, gardent leur nom en cas de chevauchement. */
private fun riverLabels(data: MapData): List<MapLabel> = data.catalog.inCategory(Category.RIVER).mapIndexedNotNull { index, river ->
    val shape = data.geometry[river.id] ?: return@mapIndexedNotNull null
    MapLabel(river.name, shape.label, fitWidth = 1e6f - index, aboveDp = 8f, style = LabelStyle.WATER)
}

/** Cours d'eau en bleu (le sélectionné plus épais et plus foncé) sur un fond de départements neutre et non touchable. */
private fun riverStyle(data: MapData, id: String, highlighted: Set<String>): ShapeStyle? {
    val shape = data.geometry[id] ?: return null
    return when (shape.kind) {
        ShapeKind.POINT -> null
        ShapeKind.POLYGON -> if (data.catalog[id]?.type != FeatureType.DEPARTMENT) null else ShapeStyle(DEFAULT_COLOR, smallMarker = false, tappable = false)
        ShapeKind.LINE -> if (id in highlighted) {
            ShapeStyle(SELECTED_RIVER_COLOR, emphasized = true, lineWidthDp = 5f)
        } else {
            ShapeStyle(RIVER_COLOR, lineWidthDp = 2.5f)
        }
    }
}

/**
 * Massifs en capitales espacées au milieu de leur zone ; sommets au-dessus de leur triangle, avec l'altitude.
 * Les massifs d'abord : ce sont eux qui gardent leur nom en cas de chevauchement.
 */
private fun mountainLabels(data: MapData): List<MapLabel> {
    val massifs = data.catalog.entities.filter { it.type == FeatureType.MASSIF }.mapNotNull { massif ->
        val shape = data.geometry[massif.id] ?: return@mapNotNull null
        MapLabel(massif.name.uppercase(), shape.label, fitWidth = 2e6f, style = LabelStyle.RELIEF)
    }
    val peaks = data.catalog.entities.filter { it.type == FeatureType.PEAK }.mapNotNull { peak ->
        val shape = data.geometry[peak.id] ?: return@mapNotNull null
        MapLabel("${peak.name} ${peak.altitudeM ?: ""}", shape.label, fitWidth = 1e6f + (peak.altitudeM ?: 0), aboveDp = 12f)
    }
    return massifs + peaks
}

/** Massifs en couleurs de relief (le sélectionné plus soutenu), sommets en triangles, départements en fond. */
private fun mountainStyle(data: MapData, id: String, highlighted: Set<String>): ShapeStyle? {
    val selected = id in highlighted
    return when (data.catalog[id]?.type) {
        FeatureType.DEPARTMENT -> ShapeStyle(DEFAULT_COLOR, smallMarker = false, tappable = false)
        FeatureType.MASSIF -> ShapeStyle(if (selected) MASSIF_COLOR.darken(0.3f) else MASSIF_COLOR, smallMarker = false, emphasized = selected)
        FeatureType.PEAK -> ShapeStyle(if (selected) Color(0xFFD9822B) else PEAK_COLOR, emphasized = selected, triangle = true)
        else -> null
    }
}

private val MASSIF_COLOR = Color(0xFFD8B98A)
private val PEAK_COLOR = Color(0xFF6B3E1F)

private val RIVER_COLOR = Color(0xFF2F7FD8)
private val SELECTED_RIVER_COLOR = Color(0xFF0B3E8A)

/** Couleurs pastel par région, deux régions voisines n'ayant jamais la même. */
private val REGION_COLORS = mapOf(
    "REG-IDF" to Color(0xFFF4B6C2),
    "REG-CVL" to Color(0xFFF2C98A),
    "REG-BFC" to Color(0xFFD7B8E8),
    "REG-NOR" to Color(0xFF9CC3E6),
    "REG-HDF" to Color(0xFFA8D5A2),
    "REG-GES" to Color(0xFFF0E39A),
    "REG-PDL" to Color(0xFFB5E0DC),
    "REG-BRE" to Color(0xFFF7D6A8),
    "REG-NAQ" to Color(0xFFC9E4A6),
    "REG-OCC" to Color(0xFFF5B7A0),
    "REG-ARA" to Color(0xFFA9C8F0),
    "REG-PAC" to Color(0xFFE8C2E0),
    "REG-COR" to Color(0xFFF0E39A),
    "REG-GUA" to Color(0xFFA8D5A2),
    "REG-MTQ" to Color(0xFFF2C98A),
    "REG-GUY" to Color(0xFF9CC3E6),
    "REG-REU" to Color(0xFFF5B7A0),
    "REG-MAY" to Color(0xFFD7B8E8),
)
private val DEFAULT_COLOR = Color(0xFFE0E0E0)
private val COLLECTIVITY_COLOR = Color(0xFFB8D8C8)
private val PARK_COLOR = Color(0xFF3E9E52)

/** Chef-lieu d'une collectivité d'outre-mer (pas d'un département). */
private fun isCollectivityCapital(data: MapData, id: String): Boolean =
    data.catalog[id]?.parentId?.let { data.catalog[it] }?.type == FeatureType.COLLECTIVITY

/** Noms courts des parcs (« Vanoise »), écrits sur leur partie terrestre. */
private fun parkLabels(data: MapData): List<MapLabel> = data.catalog.entities.filter { it.type == FeatureType.PARK }.mapNotNull { park ->
    val shape = data.geometry[park.id] ?: return@mapNotNull null
    MapLabel(park.aliases.firstOrNull() ?: park.name, shape.label, fitWidth = 2e6f)
}

/** Parcs nationaux en vert (le sélectionné plus soutenu) sur un fond de départements neutre et non touchable. */
private fun parkStyle(data: MapData, id: String, highlighted: Set<String>): ShapeStyle? {
    val selected = id in highlighted
    return when (data.catalog[id]?.type) {
        FeatureType.DEPARTMENT -> ShapeStyle(DEFAULT_COLOR, smallMarker = false, tappable = false)
        FeatureType.PARK -> ShapeStyle(if (selected) PARK_COLOR.darken(0.35f) else PARK_COLOR, emphasized = selected, outline = false)
        else -> null
    }
}
private val CAPITAL_COLOR = Color(0xFF3A3A3A)

/**
 * Départements colorés selon leur région ; le lieu (ou la région) sélectionné est plus soutenu.
 * Chefs-lieux : petits points sombres non touchables (en vue régions, seulement ceux des régions).
 */
private fun learnStyle(
    data: MapData,
    id: String,
    highlighted: Set<String>,
    regionView: Boolean,
    regionCapitals: Set<String>,
    withCollectivities: Boolean,
): ShapeStyle? {
    val shape = data.geometry[id] ?: return null
    val type = data.catalog[id]?.type
    // Cours d'eau, massifs, sommets et parcs : dans leur propre catégorie ; collectivités : dans Outre-mer.
    val shown = setOf(FeatureType.DEPARTMENT, FeatureType.PREFECTURE) + if (withCollectivities) setOf(FeatureType.COLLECTIVITY) else emptySet()
    if (type !in shown) return null
    if (shape.kind == ShapeKind.POINT) {
        if (regionView && id !in regionCapitals) return null
        if (!withCollectivities && isCollectivityCapital(data, id)) return null
        return ShapeStyle(CAPITAL_COLOR, tappable = false, largePoint = false)
    }
    val base = if (type == FeatureType.COLLECTIVITY) COLLECTIVITY_COLOR else data.catalog.regionOf(id)?.let { REGION_COLORS[it.id] } ?: DEFAULT_COLOR
    val selected = id in highlighted
    return ShapeStyle(fill = if (selected) base.darken(0.35f) else base, emphasized = selected)
}

private fun Color.darken(amount: Float) = Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)
