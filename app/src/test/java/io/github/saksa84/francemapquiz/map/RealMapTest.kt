package io.github.saksa84.francemapquiz.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Vraie carte livrée (`map.geometry.json`) : cadres, chefs-lieux à l'intérieur de leur département,
 * et sélection au doigt, y compris dans la petite couronne parisienne.
 */
class RealMapTest {

    private val geometry = MapGeometry.parse(File("src/main/assets/geo/map.geometry.json").readText())
    private val departments = geometry.shapes.filter { it.kind == ShapeKind.POLYGON }
    private val capitals = geometry.shapes.filter { it.kind == ShapeKind.POINT }

    /** En jeu, seuls les départements sont touchables (les chefs-lieux ne sont pas dessinés). */
    private val tester = HitTester(departments)

    /**
     * Tolérance au doigt en vue d'ensemble sur un Pixel 8 Pro : 16 dp ≈ 56 px pour environ
     * 1 340 px de largeur de carte, soit ≈ 4 % de la largeur totale de la carte.
     */
    private val overviewTolerance = geometry.bounds.width * 56f / 1340f

    @Test
    fun has101DepartmentsAnd101Capitals_inSixFrames() {
        assertEquals(101, departments.size)
        assertEquals(101, capitals.size)
        assertEquals(listOf("metro", "971", "972", "973", "974", "976"), geometry.frames.map { it.id })
        assertEquals(5, departments.count { it.frame != "metro" })
    }

    @Test
    fun everyShapeLiesInsideItsFrame() {
        geometry.shapes.forEach { shape ->
            val frame = geometry.frame(shape.frame)
            assertNotNull(shape.id, frame)
            val b = shape.bounds
            val f = frame!!.bounds
            assertTrue(shape.id, b.minX >= f.minX && b.maxX <= f.maxX && b.minY >= f.minY && b.maxY <= f.maxY)
        }
    }

    @Test
    fun framesDoNotOverlap() {
        val frames = geometry.frames.map { it.bounds }
        for (i in frames.indices) for (j in i + 1 until frames.size) {
            val a = frames[i]
            val b = frames[j]
            val overlap = a.minX < b.maxX && b.minX < a.maxX && a.minY < b.maxY && b.minY < a.maxY
            assertTrue("${geometry.frames[i].id} / ${geometry.frames[j].id}", !overlap)
        }
    }

    /** Leçon de China Map Quiz : un point d'étiquette ou un chef-lieu doit tomber dans son polygone. */
    @Test
    fun everyCapitalLiesInsideItsDepartment() {
        capitals.forEach { capital ->
            val department = geometry[capital.id.replace("CL-", "DEP-")]
            assertNotNull(capital.id, department)
            assertTrue("${capital.id} hors de son département", department!!.contains(capital.label.x, capital.label.y))
            assertEquals(capital.id, department.id, tester.hit(capital.label))
        }
    }

    @Test
    fun labelPointsLieInsideTheirShape() {
        departments.forEach { shape ->
            assertTrue("Étiquette hors de ${shape.id}", shape.contains(shape.label.x, shape.label.y))
        }
    }

    /** Petite couronne : Paris et ses voisins restent sélectionnables, même avec la tolérance de la vue d'ensemble. */
    @Test
    fun smallParisDepartmentsAreReachable() {
        for (code in listOf("75", "92", "93", "94")) {
            val shape = geometry["DEP-$code"]!!
            assertEquals(shape.id, tester.hit(shape.label))
            assertEquals(shape.id, tester.hit(shape.label, overviewTolerance))
        }
        // Le chef-lieu de Paris est dans Paris, celui des Hauts-de-Seine (Nanterre) dans les Hauts-de-Seine.
        assertEquals("DEP-75", tester.hit(geometry["CL-75"]!!.label, overviewTolerance))
        assertEquals("DEP-92", tester.hit(geometry["CL-92"]!!.label, overviewTolerance))
    }

    @Test
    fun parisIsTheSmallestDepartment_andIsSurroundedByTheInnerRing() {
        val paris = geometry["DEP-75"]!!
        assertEquals("DEP-75", departments.filter { it.frame == "metro" }.minBy { it.area }.id)
        // Juste à l'extérieur de Paris, de chaque côté : un département de la petite couronne.
        val b = paris.bounds
        val margin = 5f // 500 m
        val around = listOf(
            MapPoint(b.minX - margin, paris.label.y), MapPoint(b.maxX + margin, paris.label.y),
            MapPoint(paris.label.x, b.minY - margin), MapPoint(paris.label.x, b.maxY + margin),
        ).map { tester.hit(it) }
        around.forEach { assertTrue("$around", it in setOf("DEP-92", "DEP-93", "DEP-94")) }
    }

    @Test
    fun seaAndInsetMarginsAreNothing_withoutTolerance() {
        val metro = geometry.frame("metro")!!.bounds
        assertNull(tester.hit(MapPoint(metro.minX + 10f, metro.minY + 10f))) // coin nord-ouest : Manche
        geometry.frames.filter { it.id != "metro" }.forEach { frame ->
            assertNull(frame.id, tester.hit(MapPoint(frame.bounds.minX + 2f, frame.bounds.minY + 2f)))
        }
    }

    @Test
    fun corsicaIsInTheMetropolitanFrame_withBothDepartments() {
        assertEquals("metro", geometry["DEP-2A"]!!.frame)
        assertEquals("metro", geometry["DEP-2B"]!!.frame)
        // Haute-Corse au nord de la Corse-du-Sud (y vers le bas).
        assertTrue(geometry["DEP-2B"]!!.label.y < geometry["DEP-2A"]!!.label.y)
    }

    @Test
    fun northIsUp() {
        val lille = geometry["CL-59"]!!.label
        val perpignan = geometry["CL-66"]!!.label
        assertTrue("y vers le bas : le nord a un y plus petit", lille.y < perpignan.y)
        val brest = geometry["DEP-29"]!!.label
        val strasbourg = geometry["CL-67"]!!.label
        assertTrue("l'ouest à gauche", brest.x < strasbourg.x)
    }

    @Test
    fun importantIslandsAreKept() {
        // Belle-Île (Morbihan), Oléron (Charente-Maritime), Ré : plusieurs anneaux pour ces départements.
        assertTrue(geometry["DEP-56"]!!.rings.size > 1)
        assertTrue(geometry["DEP-17"]!!.rings.size > 2)
        // Guadeloupe : Basse-Terre, Grande-Terre (souvent un seul anneau), Marie-Galante, les Saintes, la Désirade.
        assertTrue(geometry["DEP-971"]!!.rings.size >= 4)
    }

    @Test
    fun contextCountriesAreDrawnButAreNotDepartments() {
        assertTrue(geometry.context.isNotEmpty())
        assertTrue(geometry.context.none { geometry[it.id] != null })
    }
}
