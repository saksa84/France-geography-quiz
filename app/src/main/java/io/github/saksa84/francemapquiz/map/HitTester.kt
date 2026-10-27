package io.github.saksa84.francemapquiz.map

/**
 * Trouve le lieu touché, parmi ceux qu'accepte [accept] (le niveau en cours, par exemple).
 *
 * Ordre de priorité :
 * 1. un point (chef-lieu, sommet) ou une ligne (cours d'eau) dans le rayon de tolérance : ce sont de
 *    petites cibles, posées par-dessus les surfaces (un chef-lieu dans son département) ;
 * 2. une surface qui contient exactement le toucher ; si plusieurs se superposent (un site dans un
 *    massif, plus tard), la plus petite, sinon elle serait inaccessible ;
 * 3. la surface la plus proche dans le rayon de tolérance, pour que Paris ou une petite île
 *    restent sélectionnables au doigt.
 */
class HitTester(private val shapes: List<MapShape>) {

    /** @param tolerance rayon de tolérance dans le repère de la carte (0 : toucher exact uniquement). */
    fun hit(point: MapPoint, tolerance: Float = 0f, accept: (MapShape) -> Boolean = { true }): String? {
        if (tolerance > 0f) {
            nearest(point, tolerance) { it.kind != ShapeKind.POLYGON && accept(it) }?.let { return it }
        }
        shapes.filter { it.kind == ShapeKind.POLYGON && accept(it) && it.contains(point.x, point.y) }
            .minByOrNull { it.area }?.let { return it.id }
        if (tolerance <= 0f) return null
        return nearest(point, tolerance) { it.kind == ShapeKind.POLYGON && accept(it) }
    }

    private fun nearest(point: MapPoint, tolerance: Float, filter: (MapShape) -> Boolean): String? {
        var bestId: String? = null
        var bestDistance = tolerance
        for (shape in shapes) {
            if (!filter(shape) || !shape.bounds.expandedBy(tolerance).contains(point.x, point.y)) continue
            val distance = shape.distanceToEdge(point.x, point.y)
            if (distance <= bestDistance) {
                bestDistance = distance
                bestId = shape.id
            }
        }
        return bestId
    }
}
