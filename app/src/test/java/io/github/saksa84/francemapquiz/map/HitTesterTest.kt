package io.github.saksa84.francemapquiz.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitTesterTest {

    private fun square(x0: Float, y0: Float, size: Float) = floatArrayOf(x0, y0, x0 + size, y0, x0 + size, y0 + size, x0, y0 + size)

    /** Carré 0..10 percé d'un trou 4..6, plus une petite île 20..21. */
    private val withHole = MapShape("A", listOf(square(0f, 0f, 10f), square(4f, 4f, 2f), square(20f, 0f, 1f)))
    private val neighbour = MapShape("B", listOf(square(10f, 0f, 5f)))
    private val tester = HitTester(listOf(withHole, neighbour))

    @Test
    fun pointInside_isFound() {
        assertEquals("A", tester.hit(MapPoint(2f, 2f)))
        assertEquals("B", tester.hit(MapPoint(12f, 2f)))
    }

    @Test
    fun pointInHole_isNotInside() {
        assertFalse(withHole.contains(5f, 5f))
        assertNull(tester.hit(MapPoint(5f, 5f)))
    }

    @Test
    fun pointOnSeparateIsland_belongsToItsShape() {
        assertEquals("A", tester.hit(MapPoint(20.5f, 0.5f)))
    }

    @Test
    fun pointInSea_withoutTolerance_isNothing() {
        assertNull(tester.hit(MapPoint(30f, 30f)))
        assertNull(tester.hit(MapPoint(22f, 0.5f)))
    }

    @Test
    fun overlappingAreas_theSmallestWins() {
        // Une petite zone dans une grande (un lac dans un massif, en version 2), dans n'importe quel ordre.
        val big = MapShape("BIG", listOf(square(0f, 0f, 100f)), overlay = true)
        val small = MapShape("SMALL", listOf(square(40f, 40f, 10f)), overlay = true)
        assertEquals("SMALL", HitTester(listOf(big, small)).hit(MapPoint(45f, 45f)))
        assertEquals("SMALL", HitTester(listOf(small, big)).hit(MapPoint(45f, 45f)))
        assertEquals("BIG", HitTester(listOf(small, big)).hit(MapPoint(10f, 10f)))
    }

    @Test
    fun pointNearSmallIsland_isFoundWithinTolerance() {
        assertEquals("A", tester.hit(MapPoint(22f, 0.5f), tolerance = 1.5f))
        assertNull(tester.hit(MapPoint(25f, 0.5f), tolerance = 1.5f))
    }

    @Test
    fun toleranceChoosesTheClosestShape() {
        // Au-dessus de la frontière A/B (x = 10), un peu plus près de B.
        assertEquals("B", tester.hit(MapPoint(10.4f, -1f), tolerance = 3f))
        assertEquals("A", tester.hit(MapPoint(9.6f, -1f), tolerance = 3f))
    }

    @Test
    fun exactHitWinsOverCloserNeighbourEdge() {
        assertEquals("A", tester.hit(MapPoint(9.9f, 2f), tolerance = 3f))
    }

    // --- Lignes et points ---

    /** Rivière horizontale de (0, 30) à (20, 30), ville en (5, 5) dans la surface A. */
    private val river = MapShape("R", listOf(floatArrayOf(0f, 30f, 10f, 30f, 20f, 30f)), kind = ShapeKind.LINE)
    private val city = MapShape("C", listOf(floatArrayOf(5f, 5f)), kind = ShapeKind.POINT)
    private val all = HitTester(listOf(withHole, neighbour, river, city))

    @Test
    fun lineIsFoundNearItsTrace_butIsNotASurface() {
        assertEquals("R", all.hit(MapPoint(12f, 31f), tolerance = 2f))
        assertFalse(river.contains(12f, 30f))
        assertEquals(0f, river.area)
    }

    @Test
    fun openLineHasNoClosingSegment() {
        // Le point (10, 30 - 1) est près du tracé ; le milieu du « segment de fermeture » imaginaire n'existe pas.
        assertEquals(1f, river.distanceToEdge(10f, 29f), 1e-4f)
        assertEquals(10f, river.distanceToEdge(-10f, 30f), 1e-4f)
    }

    @Test
    fun pointBeatsTheSurfaceItSitsOn() {
        // La ville est dans la surface A : un toucher proche de la ville la sélectionne, elle.
        assertEquals("C", all.hit(MapPoint(5.5f, 5f), tolerance = 1f))
        // Plus loin, c'est la surface qui l'emporte.
        assertEquals("A", all.hit(MapPoint(2f, 2f), tolerance = 1f))
    }

    @Test
    fun acceptFiltersOutShapesThatAreNotInPlay() {
        assertEquals("A", all.hit(MapPoint(5.5f, 5f), tolerance = 1f) { it.id != "C" })
        assertNull(all.hit(MapPoint(12f, 2f), tolerance = 1f) { it.id == "R" })
    }

    @Test
    fun areaSumsAllRings() {
        // 100 (carré) + 4 (trou, compté positivement) + 1 (île)
        assertEquals(105f, withHole.area, 1e-3f)
        assertEquals(25f, neighbour.area, 1e-3f)
    }

    @Test
    fun boundsCoverAllRings() {
        assertEquals(MapBounds(0f, 0f, 21f, 10f), withHole.bounds)
        assertTrue(withHole.distanceToEdge(2f, 5f) == 2f)
    }
}
