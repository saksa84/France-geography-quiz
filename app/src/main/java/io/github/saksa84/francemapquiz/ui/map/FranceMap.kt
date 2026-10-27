package io.github.saksa84.francemapquiz.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saksa84.francemapquiz.map.HitTester
import io.github.saksa84.francemapquiz.map.MapBounds
import io.github.saksa84.francemapquiz.map.MapGeometry
import io.github.saksa84.francemapquiz.map.MapPoint
import io.github.saksa84.francemapquiz.map.MapShape
import io.github.saksa84.francemapquiz.map.MapViewport
import io.github.saksa84.francemapquiz.map.ShapeKind

/**
 * Apparence d'un lieu sur la carte, décidée par l'écran appelant (Apprendre, Trouver…).
 * @param fill couleur de remplissage (surface, point) ou du trait (cours d'eau)
 * @param smallMarker quand le lieu est trop petit pour être vu, le dessiner comme un point grossi
 * @param emphasized lieu sélectionné ou montré en réponse : s'il est minuscule, un réticule l'entoure
 * @param tappable le lieu peut être touché (faux pour un chef-lieu, simple repère)
 * @param lineWidthDp épaisseur du trait d'un cours d'eau, constante à l'écran
 * @param largePoint lieu ponctuel dessiné en gros point ; faux pour un simple repère (chef-lieu)
 */
data class ShapeStyle(
    val fill: Color,
    val dashedBorder: Boolean = false,
    val smallMarker: Boolean = true,
    val emphasized: Boolean = false,
    val tappable: Boolean = true,
    val lineWidthDp: Float = 2f,
    val largePoint: Boolean = true,
)

/**
 * Nom à écrire sur la carte.
 * @param fitWidth largeur disponible (unités de carte) : le nom n'est écrit que s'il y tient
 * @param aboveDp décalage vers le haut à l'écran (nom d'un chef-lieu écrit au-dessus de son point)
 */
data class MapLabel(val text: String, val at: MapPoint, val fitWidth: Float, val aboveDp: Float = 0f)

/**
 * Carte interactive : dessin des contours, zoom à deux doigts, déplacement, toucher.
 *
 * Ordre de dessin : mer, pays voisins (dans le cadre de la métropole seulement), cadres des
 * cartouches de l'outre-mer, départements, puis chefs-lieux sous forme de points.
 *
 * @param styleOf apparence de chaque lieu selon son identifiant ; null : lieu masqué et non touchable
 * @param selectedIds lieux à mettre en évidence (contour épais) : un département, ou ceux d'une région
 * @param recenterKey changer cette valeur remet la carte dans sa vue de départ
 * @param focus zone sur laquelle cadrer la vue de départ (la région d'un niveau) ; null : carte entière
 * @param onShapeTap appelé avec l'identifiant touché, ou null si on touche la mer
 */
@Composable
fun FranceMap(
    geometry: MapGeometry,
    styleOf: (String) -> ShapeStyle?,
    selectedIds: Set<String>,
    onShapeTap: (String?) -> Unit,
    modifier: Modifier = Modifier,
    recenterKey: Int = 0,
    focus: MapBounds? = null,
    labels: List<MapLabel> = emptyList(),
    seaColor: Color = Color(0xFFD6E9F8),
    contextColor: Color = Color(0xFFE8E4DC),
    borderColor: Color = Color(0xFF5A6470),
    selectedBorderColor: Color = Color(0xFF1B1B1B),
    labelColor: Color = Color(0xFF263033),
) {
    // Les contours Compose sont construits une seule fois : ils sont ensuite seulement redimensionnés.
    val paths = remember(geometry) { geometry.shapes.filter { it.kind != ShapeKind.POINT }.associate { it.id to it.toPath() } }
    val contextPaths = remember(geometry) { geometry.context.map { it.toPath() } }
    val baseShapes = remember(geometry) { geometry.shapes.filter { !it.overlay } }
    val overlayLines = remember(geometry) { geometry.shapes.filter { it.kind == ShapeKind.LINE } }
    val hitTester = remember(geometry) { HitTester(geometry.shapes) }
    val metroFrame = remember(geometry) { geometry.frame("metro") }
    val insetFrames = remember(geometry) { geometry.frames.filter { it.id != "metro" } }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember { mutableStateOf<MapViewport?>(null) }
    var lastRecenterKey by remember { mutableStateOf(recenterKey) }
    // Dernière vue de départ calculée : tant que l'utilisateur n'a pas bougé la carte, on y est encore.
    var home by remember { mutableStateOf<MapViewport?>(null) }

    // Vue de départ (carte entière, ou cadrée sur la région du niveau) au premier affichage et sur
    // demande. Un changement de taille (clavier, rotation, panneau) recadre la carte si l'on était
    // dans la vue de départ, mais n'annule pas un zoom choisi par l'utilisateur.
    LaunchedEffect(viewSize, recenterKey, focus) {
        if (viewSize == IntSize.Zero) return@LaunchedEffect
        val width = viewSize.width.toFloat()
        val height = viewSize.height.toFloat()
        val current = viewport
        val start = focus?.let { MapViewport.focused(geometry.bounds, it, width, height) }
            ?: MapViewport.fit(geometry.bounds, width, height)
        viewport = if (current == null || current == home || recenterKey != lastRecenterKey) {
            start
        } else {
            current.constrainedTo(geometry.bounds, width, height)
        }
        home = start
        lastRecenterKey = recenterKey
    }

    val touchTolerancePx = with(LocalDensity.current) { 16.dp.toPx() }
    val borderPx = with(LocalDensity.current) { 0.8.dp.toPx() }
    val selectedBorderPx = with(LocalDensity.current) { 2.5.dp.toPx() }
    val contextStrokePx = with(LocalDensity.current) { 2.dp.toPx() }
    // Lieux dont la surface à l'écran est inférieure à un carré de ce côté (Paris en vue
    // d'ensemble) : dessinés comme un point grossi, pour rester visibles et touchables.
    val tinyThresholdPx = with(LocalDensity.current) { 6.dp.toPx() }
    val dotRadiusPx = with(LocalDensity.current) { 3.dp.toPx() }
    val pointRadiusPx = with(LocalDensity.current) { 4.5.dp.toPx() }
    val capitalRadiusPx = with(LocalDensity.current) { 2.5.dp.toPx() }
    val dotBorderPx = with(LocalDensity.current) { 1.dp.toPx() }
    val reticleRadiusPx = with(LocalDensity.current) { 46.dp.toPx() }
    val reticleStrokePx = with(LocalDensity.current) { 1.5.dp.toPx() }

    // Les noms sont mesurés une fois ; on ne décide qu'au dessin lesquels ont la place d'apparaître.
    val textMeasurer = rememberTextMeasurer()
    val measuredLabels = remember(labels, labelColor) {
        val style = TextStyle(
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            shadow = Shadow(if (labelColor.luminance() > 0.5f) Color.Black else Color.White, blurRadius = 6f),
        )
        // Les plus grands lieux d'abord : en cas de chevauchement, ce sont eux qui gardent leur nom.
        labels.sortedByDescending { it.fitWidth }.map { it to textMeasurer.measure(it.text, style) }
    }
    // Les gestionnaires de gestes sont créés une fois : ils lisent toujours la dernière valeur via ces références.
    val currentViewport by rememberUpdatedState(viewport)
    val currentOnTap by rememberUpdatedState(onShapeTap)
    val currentStyleOf by rememberUpdatedState(styleOf)

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewSize = it }
            .pointerInput(geometry) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val current = currentViewport ?: return@detectTransformGestures
                    viewport = current
                        .zoomedAt(centroid.x, centroid.y, zoom)
                        .pannedBy(pan.x, pan.y)
                        .constrainedTo(geometry.bounds, size.width.toFloat(), size.height.toFloat())
                }
            }
            .pointerInput(geometry) {
                detectTapGestures { tap ->
                    val current = currentViewport ?: return@detectTapGestures
                    val point = current.toMap(tap.x, tap.y)
                    val tolerance = touchTolerancePx / current.scale
                    currentOnTap(hitTester.hit(point, tolerance) { currentStyleOf(it.id)?.tappable == true })
                }
            },
    ) {
        drawRect(seaColor)
        val vp = viewport ?: return@Canvas

        withTransform({
            translate(vp.offsetX, vp.offsetY)
            scale(vp.scale, vp.scale, pivot = Offset.Zero)
        }) {
            // Les épaisseurs sont divisées par le zoom pour rester constantes à l'écran.
            val border = Stroke(width = borderPx / vp.scale)
            val dashed = Stroke(
                width = borderPx / vp.scale,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f / vp.scale, 4f / vp.scale)),
            )
            // Pays voisins, limités au cadre de la métropole. Un trait de la même couleur comble les
            // petits écarts entre leurs frontières (Natural Earth) et celles des départements (IGN).
            metroFrame?.bounds?.let { b ->
                clipRect(b.minX, b.minY, b.maxX, b.maxY) {
                    for (path in contextPaths) {
                        drawPath(path, contextColor)
                        drawPath(path, contextColor, style = Stroke(contextStrokePx / vp.scale, join = StrokeJoin.Round))
                    }
                }
            }
            for (frame in insetFrames) {
                val b = frame.bounds
                drawRect(borderColor.copy(alpha = 0.5f), Offset(b.minX, b.minY), Size(b.width, b.height), style = border)
            }
            for (shape in baseShapes) {
                val style = styleOf(shape.id) ?: continue
                val path = paths.getValue(shape.id)
                drawPath(path, style.fill)
                drawPath(path, borderColor, style = if (style.dashedBorder) dashed else border)
            }
            for (shape in overlayLines) {
                val style = styleOf(shape.id) ?: continue
                val width = style.lineWidthDp * density / vp.scale
                drawPath(paths.getValue(shape.id), style.fill, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // Les lieux sélectionnés sont redessinés en dernier pour que leur contour passe au-dessus des voisins.
            for (id in selectedIds) {
                // Un cours d'eau sélectionné change de couleur (voir son style) : pas de contour.
                if (geometry[id]?.kind == ShapeKind.LINE) continue
                val path = paths[id] ?: continue
                drawPath(path, selectedBorderColor, style = Stroke(width = selectedBorderPx / vp.scale, join = StrokeJoin.Round))
            }
        }

        // Noms : seulement ceux qui tiennent dans leur lieu à ce zoom, sans chevaucher un nom déjà écrit.
        // Plus on zoome, plus il y a de place : les noms des petits départements apparaissent.
        val placed = mutableListOf<Rect>()
        for ((label, layout) in measuredLabels) {
            val width = layout.size.width.toFloat()
            val height = layout.size.height.toFloat()
            if (width > label.fitWidth * vp.scale) continue
            val x = label.at.x * vp.scale + vp.offsetX - width / 2
            val y = label.at.y * vp.scale + vp.offsetY - height / 2 - label.aboveDp * density
            val box = Rect(x - 2, y, x + width + 2, y + height)
            if (placed.any { it.overlaps(box) }) continue
            placed += box
            drawText(layout, topLeft = Offset(x, y))
        }

        // Lieux minuscules à ce zoom (chefs-lieux, Paris…), dessinés en coordonnées écran pour garder une taille fixe.
        val tiny = geometry.shapes.mapNotNull { shape ->
            if (shape.kind == ShapeKind.LINE) return@mapNotNull null
            val style = styleOf(shape.id) ?: return@mapNotNull null
            if (!style.smallMarker || shape.area * vp.scale * vp.scale >= tinyThresholdPx * tinyThresholdPx) return@mapNotNull null
            val b = shape.bounds
            Triple(shape, style, Offset((b.minX + b.maxX) / 2 * vp.scale + vp.offsetX, (b.minY + b.maxY) / 2 * vp.scale + vp.offsetY))
        }
        // D'abord les réticules (sélection, réponse), puis les points par-dessus.
        for ((_, style, center) in tiny) {
            if (style.emphasized) drawReticle(center, reticleRadiusPx, reticleStrokePx)
        }
        for ((shape, style, center) in tiny) {
            val radius = when {
                shape.kind != ShapeKind.POINT -> dotRadiusPx
                style.largePoint -> pointRadiusPx
                else -> capitalRadiusPx
            }
            drawCircle(style.fill, radius = radius, center = center)
            val (ringColor, ringWidth) = if (shape.id in selectedIds) selectedBorderColor to dotBorderPx * 2 else borderColor to dotBorderPx
            drawCircle(ringColor, radius = radius, center = center, style = Stroke(ringWidth))
        }
    }
}

/**
 * Réticule autour d'un lieu minuscule sélectionné : disque sombre translucide, repères de visée
 * aux quatre points cardinaux et croix pointillée, pour voir clairement ce qui est choisi.
 */
private fun DrawScope.drawReticle(center: Offset, radius: Float, stroke: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.30f)), center, radius),
        radius = radius,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = 0.35f), radius = radius, center = center, style = Stroke(stroke))
    val dashed = PathEffect.dashPathEffect(floatArrayOf(stroke * 2, stroke * 3))
    val tick = radius * 0.2f
    for (direction in listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))) {
        drawLine(Color.White.copy(alpha = 0.7f), center + direction * (radius - tick), center + direction * radius, stroke)
        drawLine(Color.White.copy(alpha = 0.25f), center + direction * (radius * 0.25f), center + direction * (radius - tick), stroke * 0.7f, pathEffect = dashed)
    }
}

private fun MapShape.toPath(): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    for (ring in rings) {
        moveTo(ring[0], ring[1])
        for (i in 2 until ring.size step 2) lineTo(ring[i], ring[i + 1])
        if (kind == ShapeKind.POLYGON) close() // un cours d'eau reste ouvert
    }
}
