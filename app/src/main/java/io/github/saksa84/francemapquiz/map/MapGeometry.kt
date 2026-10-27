package io.github.saksa84.francemapquiz.map

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Point dans le repère de la carte : unités de [MapGeometry.unitKm] km en métropole, y vers le bas. */
data class MapPoint(val x: Float, val y: Float)

data class MapBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY

    fun contains(x: Float, y: Float): Boolean = x in minX..maxX && y in minY..maxY

    fun expandedBy(margin: Float) = MapBounds(minX - margin, minY - margin, maxX + margin, maxY + margin)
}

/** Forme d'un lieu sur la carte. */
enum class ShapeKind {
    /** Surface (département) : anneaux fermés. */
    POLYGON,

    /** Cours d'eau (version 2) : tronçons ouverts. */
    LINE,

    /** Chef-lieu, sommet : un seul point. */
    POINT,
}

/**
 * Cadre de la carte : la métropole, ou le cartouche d'un département d'outre-mer, dessiné à sa propre échelle.
 * @param scale échelle par rapport à la métropole (×4,7 pour Mayotte, ×0,47 pour la Guyane)
 */
data class MapFrame(val id: String, val bounds: MapBounds, val scale: Float = 1f)

/**
 * Géométrie d'un lieu : suites aplaties [x0, y0, x1, y1, …].
 * - Surface : anneaux extérieurs, trous et îles mélangés ; la règle pair-impair suffit à savoir si un point est dedans.
 * - Ligne : tronçons ouverts (pas de segment entre le dernier point et le premier).
 * - Point : une seule paire de coordonnées.
 *
 * @param overlay lieu posé par-dessus le fond des départements (chef-lieu ; plus tard fleuve, massif)
 * @param frame cadre où se trouve le lieu (« metro », « 971 »…)
 */
class MapShape(
    val id: String,
    val rings: List<FloatArray>,
    /** Où écrire le nom : point intérieur loin des bords (calculé par le script), sinon centre du cadre. */
    label: MapPoint? = null,
    /** Distance entre [label] et le bord le plus proche : place disponible pour écrire le nom. */
    labelRoom: Float? = null,
    val kind: ShapeKind = ShapeKind.POLYGON,
    val overlay: Boolean = false,
    val frame: String = "metro",
) {
    val bounds: MapBounds = run {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (ring in rings) {
            for (i in ring.indices step 2) {
                minX = min(minX, ring[i]); maxX = max(maxX, ring[i])
                minY = min(minY, ring[i + 1]); maxY = max(maxY, ring[i + 1])
            }
        }
        MapBounds(minX, minY, maxX, maxY)
    }

    val label: MapPoint = label ?: MapPoint((bounds.minX + bounds.maxX) / 2, (bounds.minY + bounds.maxY) / 2)
    val labelRoom: Float = labelRoom ?: (minOf(bounds.width, bounds.height) / 4)

    /** Surface approximative (somme des anneaux, trous compris), en unités de carte au carré ; 0 pour une ligne ou un point. */
    val area: Float = if (kind != ShapeKind.POLYGON) 0f else rings.sumOf { ring ->
        var twice = 0.0
        val n = ring.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            twice += ring[2 * i].toDouble() * ring[2 * j + 1] - ring[2 * j].toDouble() * ring[2 * i + 1]
        }
        kotlin.math.abs(twice) / 2
    }.toFloat()

    /** Test pair-impair : un rayon horizontal partant du point coupe un nombre impair d'arêtes. Toujours faux hors surface. */
    fun contains(x: Float, y: Float): Boolean {
        if (kind != ShapeKind.POLYGON || !bounds.contains(x, y)) return false
        var inside = false
        for (ring in rings) {
            val n = ring.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = ring[2 * i]; val yi = ring[2 * i + 1]
                val xj = ring[2 * j]; val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }

    /** Distance du point au contour (surface), au tracé (ligne) ou au point lui-même ; 0 dessus. */
    fun distanceToEdge(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (ring in rings) {
            val n = ring.size / 2
            if (kind == ShapeKind.POINT || n == 1) {
                best = min(best, hypot(x - ring[0], y - ring[1]))
                continue
            }
            // Ligne : pas de segment de fermeture entre le dernier point et le premier.
            val start = if (kind == ShapeKind.LINE) 1 else 0
            var j = if (kind == ShapeKind.LINE) 0 else n - 1
            for (i in start until n) {
                best = min(best, distanceToSegment(x, y, ring[2 * j], ring[2 * j + 1], ring[2 * i], ring[2 * i + 1]))
                j = i
            }
        }
        return best
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}

/**
 * Carte prête à dessiner (`map.geometry.json`, généré par tools/build_geo.py) : départements et
 * chefs-lieux, cadres (métropole et cartouches de l'outre-mer) et fond de carte (pays voisins).
 * Les coordonnées sont déjà projetées : l'application n'a aucun calcul de projection à faire.
 */
class MapGeometry(
    val shapes: List<MapShape>,
    val bounds: MapBounds,
    val unitKm: Double,
    val frames: List<MapFrame> = emptyList(),
    /** Pays voisins de la métropole, dessinés sous les départements et découpés au cadre de la métropole. */
    val context: List<MapShape> = emptyList(),
) {
    private val byId = shapes.associateBy { it.id }

    operator fun get(id: String): MapShape? = byId[id]

    fun frame(id: String): MapFrame? = frames.firstOrNull { it.id == id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): MapGeometry {
            val file = json.decodeFromString<MapGeometryFile>(text)
            require(file.schemaVersion == 1) { "Version de géométrie non prise en charge : ${file.schemaVersion}" }
            require(file.yAxis == "down") { "Orientation de l'axe y inattendue : ${file.yAxis}" }

            val shapes = file.features.map { feature ->
                val kind = when (feature.kind) {
                    "line" -> ShapeKind.LINE
                    "point" -> ShapeKind.POINT
                    else -> ShapeKind.POLYGON
                }
                val rings = when (kind) {
                    ShapeKind.POLYGON -> feature.polygons.orEmpty().flatten()
                    ShapeKind.LINE -> feature.lines.orEmpty()
                    ShapeKind.POINT -> listOfNotNull(feature.point)
                }.map { it.toFloatArray() }
                // Un point s'étiquette à côté de lui-même ; la place disponible est alors arbitraire.
                val label = feature.label?.takeIf { it.size >= 2 }?.let { MapPoint(it[0], it[1]) }
                    ?: feature.point?.let { MapPoint(it[0], it[1]) }
                MapShape(feature.id, rings, label, feature.label?.getOrNull(2), kind, overlay = kind != ShapeKind.POLYGON, frame = feature.frame)
            }
            val context = file.context.mapIndexed { i, c -> MapShape("context-$i", c.polygons.flatten().map { it.toFloatArray() }) }
            val frames = file.frames.map { MapFrame(it.id, it.bounds.toBounds(), it.scale) }
            return MapGeometry(shapes, file.bounds.toBounds(), file.unitKm, frames, context)
        }

        private fun List<Float>.toBounds() = MapBounds(this[0], this[1], this[2], this[3])
    }
}

@Serializable
private data class FeatureGeometry(
    val id: String,
    val kind: String = "polygon",
    val frame: String = "metro",
    val polygons: List<List<List<Float>>>? = null,
    val lines: List<List<Float>>? = null,
    val point: List<Float>? = null,
    val label: List<Float>? = null,
)

@Serializable
private data class FrameGeometry(val id: String, val bounds: List<Float>, val scale: Float = 1f)

@Serializable
private data class ContextGeometry(val polygons: List<List<List<Float>>>)

@Serializable
private data class MapGeometryFile(
    val schemaVersion: Int,
    val sourceId: String,
    val unitKm: Double,
    val yAxis: String,
    val bounds: List<Float>,
    val frames: List<FrameGeometry> = emptyList(),
    val features: List<FeatureGeometry>,
    val context: List<ContextGeometry> = emptyList(),
)
