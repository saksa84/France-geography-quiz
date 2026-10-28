package io.github.saksa84.francemapquiz.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTest {

    private val bounds = MapBounds(100f, 200f, 300f, 300f) // carte 200 × 100
    private val delta = 1e-3f

    @Test
    fun fit_centersTheWholeMap() {
        val vp = MapViewport.fit(bounds, viewWidth = 1000f, viewHeight = 1000f, margin = 0f)
        assertEquals(5f, vp.scale, delta) // limité par la largeur : 1000 / 200
        val topLeft = vp.toScreen(MapPoint(100f, 200f))
        val bottomRight = vp.toScreen(MapPoint(300f, 300f))
        assertEquals(0f, topLeft.x, delta)
        assertEquals(1000f, bottomRight.x, delta)
        assertEquals(250f, topLeft.y, delta) // marge verticale égale en haut et en bas
        assertEquals(750f, bottomRight.y, delta)
    }

    @Test
    fun toMap_isTheInverseOfToScreen() {
        val vp = MapViewport(scale = 2.5f, offsetX = -40f, offsetY = 13f, minScale = 1f, maxScale = 10f)
        val p = MapPoint(123f, 456f)
        val back = vp.toScreen(p).let { vp.toMap(it.x, it.y) }
        assertEquals(p.x, back.x, delta)
        assertEquals(p.y, back.y, delta)
    }

    @Test
    fun zoom_keepsThePointUnderTheFingersStill() {
        val vp = MapViewport.fit(bounds, 1000f, 1000f)
        val under = vp.toMap(700f, 400f)
        val zoomed = vp.zoomedAt(700f, 400f, 3f)
        val after = zoomed.toScreen(under)
        assertEquals(700f, after.x, delta)
        assertEquals(400f, after.y, delta)
        assertEquals(vp.scale * 3, zoomed.scale, delta)
    }

    @Test
    fun zoom_isClampedBetweenOverviewAndMaximum() {
        val vp = MapViewport.fit(bounds, 1000f, 1000f)
        assertEquals(vp.minScale, vp.zoomedAt(500f, 500f, 0.1f).scale, delta)
        assertEquals(vp.minScale * MapViewport.MAX_ZOOM, vp.zoomedAt(500f, 500f, 1000f).scale, delta)
    }

    @Test
    fun worldMap_allowsAStrongerZoom() {
        val vp = MapViewport.fit(bounds, 1000f, 1000f, maxZoom = 1000f)
        assertEquals(vp.minScale * 1000f, vp.zoomedAt(500f, 500f, 5000f).scale, vp.minScale)
    }

    @Test
    fun focused_framesTheLevel_butKeepsTheOverviewAsMinimumZoom() {
        // Une « région » de 20 × 20 dans la carte de 200 × 100.
        val region = MapBounds(140f, 220f, 160f, 240f)
        val vp = MapViewport.focused(bounds, region, 1000f, 1000f)
        val overview = MapViewport.fit(bounds, 1000f, 1000f)
        assertTrue(vp.scale > overview.scale * 5)
        assertEquals(overview.minScale, vp.minScale, delta) // on peut dézoomer jusqu'à la carte entière
        val center = vp.toMap(500f, 500f)
        assertEquals(150f, center.x, delta)
        assertEquals(230f, center.y, delta)
    }

    @Test
    fun pan_cannotLoseTheMap() {
        val vp = MapViewport.fit(bounds, 1000f, 1000f).zoomedAt(500f, 500f, 4f)
        val lost = vp.pannedBy(1_000_000f, -1_000_000f).constrainedTo(bounds, 1000f, 1000f)
        val center = lost.toMap(500f, 500f)
        assertEquals(bounds.minX, center.x, delta) // le centre de l'écran est ramené sur le bord de la carte
        assertEquals(bounds.maxY, center.y, delta)
    }
}
