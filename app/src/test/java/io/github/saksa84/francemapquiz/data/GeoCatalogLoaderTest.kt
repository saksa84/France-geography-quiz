package io.github.saksa84.francemapquiz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chargement et validation sur de petites données écrites ici : une région, deux départements,
 * leurs chefs-lieux. Chaque test casse une seule règle pour vérifier qu'elle est bien détectée.
 */
class GeoCatalogLoaderTest {

    private val source = """{"id":"src","name":"Test","license":"Licence Ouverte"}"""

    private fun file(vararg entities: String) =
        """{"schemaVersion":1,"sources":[$source],"entities":[${entities.joinToString(",")}]}"""

    private val region = """{"id":"REG-ARA","type":"region","name":"Auvergne-Rhône-Alpes","code":"84","capitalId":"CL-69","sourceId":"src"}"""
    private val rhone = """{"id":"DEP-69","type":"department","name":"Rhône","code":"69","capitalId":"CL-69","parentId":"REG-ARA","areaKm2":3249,"sourceId":"src"}"""
    private val cantal = """{"id":"DEP-15","type":"department","name":"Cantal","code":"15","capitalId":"CL-15","parentId":"REG-ARA","sourceId":"src"}"""
    private val lyon = """{"id":"CL-69","type":"prefecture","name":"Lyon","parentId":"DEP-69","sourceId":"src"}"""
    private val aurillac = """{"id":"CL-15","type":"prefecture","name":"Aurillac","parentId":"DEP-15","sourceId":"src"}"""

    private fun valid(vararg replaced: Pair<String, String>): List<String> {
        val all = mutableListOf(region, rhone, cantal, lyon, aurillac)
        for ((old, new) in replaced) all[all.indexOf(old)] = new
        return all
    }

    private fun errorsOf(vararg entities: String): List<ValidationError> =
        when (val result = GeoCatalogLoader.load(file(*entities))) {
            is LoadResult.Invalid -> result.errors
            else -> throw AssertionError("Erreurs attendues, obtenu : $result")
        }

    @Test
    fun validData_givesACatalogWithQuickAccess() {
        val catalog = (GeoCatalogLoader.load(file(*valid().toTypedArray())) as LoadResult.Success).catalog
        assertEquals("Rhône", catalog["DEP-69"]!!.name)
        assertEquals("Rhône (69)", catalog["DEP-69"]!!.displayName)
        assertEquals("Lyon", catalog.capitalOf(catalog["DEP-69"]!!)!!.name)
        assertEquals("REG-ARA", catalog.regionOf("DEP-15")!!.id)
        assertEquals(listOf("DEP-69", "DEP-15"), catalog.departmentsOf("REG-ARA").map { it.id })
    }

    @Test
    fun filesAreValidatedTogether() {
        // La région d'un fichier, les départements d'un autre : les références se croisent.
        val result = GeoCatalogLoader.load(file(region, lyon, aurillac), file(rhone, cantal))
        assertTrue(result is LoadResult.Success)
    }

    @Test
    fun malformedJson_isReported() {
        assertTrue(GeoCatalogLoader.load("pas du json") is LoadResult.Malformed)
        assertTrue(GeoCatalogLoader.load(file("""{"id":"X","type":"volcan","name":"X","sourceId":"src"}""")) is LoadResult.Malformed)
    }

    @Test
    fun duplicateIdsAndCodes_areRejected() {
        assertTrue(ValidationError.DuplicateId("DEP-15") in errorsOf(*(valid() + cantal).toTypedArray()))
        val sameCode = cantal.replace("\"code\":\"15\"", "\"code\":\"69\"")
        val errors = errorsOf(*valid(cantal to sameCode).toTypedArray())
        assertTrue(errors.toString(), ValidationError.DuplicateCode("69") in errors)
    }

    @Test
    fun departmentCodes_mustBeOfficial() {
        // 20 (ancien code de la Corse), 96, 975 (Saint-Pierre-et-Miquelon, hors périmètre), 3 (sans zéro).
        for (bad in listOf("20", "96", "975", "3", "2C")) {
            val errors = errorsOf(*valid(cantal to cantal.replace("\"15\"", "\"$bad\"")).toTypedArray())
            assertTrue("$bad : $errors", ValidationError.InvalidCode("DEP-15", bad) in errors)
        }
        val corse = cantal.replace("\"15\"", "\"2A\"")
        assertTrue(GeoCatalogLoader.load(file(*valid(cantal to corse).toTypedArray())) is LoadResult.Success)
    }

    @Test
    fun overseasCode_andFlag_mustAgree() {
        val errors = errorsOf(*valid(cantal to cantal.replace("\"15\"", "\"974\"")).toTypedArray())
        assertTrue(errors.toString(), ValidationError.InvalidCode("DEP-15", "974") in errors)
    }

    @Test
    fun departmentNeedsAKnownRegionParent() {
        val orphan = cantal.replace(",\"parentId\":\"REG-ARA\"", "")
        assertTrue(ValidationError.MissingParent("DEP-15") in errorsOf(*valid(cantal to orphan).toTypedArray()))
        val unknown = cantal.replace("REG-ARA", "REG-XXX")
        assertTrue(ValidationError.UnknownParent("DEP-15", "REG-XXX") in errorsOf(*valid(cantal to unknown).toTypedArray()))
        val underDepartment = cantal.replace("\"parentId\":\"REG-ARA\"", "\"parentId\":\"DEP-69\"")
        assertTrue(ValidationError.InvalidParent("DEP-15", "DEP-69") in errorsOf(*valid(cantal to underDepartment).toTypedArray()))
    }

    @Test
    fun capitals_mustBelongToTheirDepartmentOrRegion() {
        // Le chef-lieu du Cantal ne peut pas être Lyon (chef-lieu d'un autre département).
        val wrong = cantal.replace("\"capitalId\":\"CL-15\"", "\"capitalId\":\"CL-69\"")
        assertTrue(ValidationError.InvalidCapital("DEP-15", "CL-69") in errorsOf(*valid(cantal to wrong).toTypedArray()))
        val missing = cantal.replace(",\"capitalId\":\"CL-15\"", "")
        assertTrue(ValidationError.MissingCapital("DEP-15") in errorsOf(*valid(cantal to missing).toTypedArray()))
        // Une région peut avoir pour chef-lieu celui de n'importe lequel de ses départements…
        val regionAurillac = region.replace("CL-69", "CL-15")
        assertTrue(GeoCatalogLoader.load(file(*valid(region to regionAurillac).toTypedArray())) is LoadResult.Success)
        // …mais pas un département lui-même.
        val regionDepartment = region.replace("CL-69", "DEP-69")
        assertTrue(ValidationError.InvalidCapital("REG-ARA", "DEP-69") in errorsOf(*valid(region to regionDepartment).toTypedArray()))
    }

    @Test
    fun namesMustBeFrench_withAccentsHyphensAndApostrophes() {
        val ok = cantal.replace("Cantal", "Côtes-d'Armor")
        assertTrue(GeoCatalogLoader.load(file(*valid(cantal to ok).toTypedArray())) is LoadResult.Success)
        val bad = cantal.replace("\"name\":\"Cantal\"", "\"name\":\" Cantal\"")
        assertTrue(ValidationError.InvalidName("DEP-15", " Cantal") in errorsOf(*valid(cantal to bad).toTypedArray()))
    }

    @Test
    fun regionWithoutDepartment_isRejected() {
        val empty = """{"id":"REG-COR","type":"region","name":"Corse","code":"94","capitalId":"CL-69","sourceId":"src"}"""
        val errors = errorsOf(*(valid() + empty).toTypedArray())
        assertTrue(errors.toString(), ValidationError.EmptyRegion("REG-COR") in errors)
    }

    @Test
    fun unknownSource_andUnsupportedSchema_areRejected() {
        val other = cantal.replace("\"sourceId\":\"src\"", "\"sourceId\":\"autre\"")
        assertTrue(ValidationError.UnknownSource("DEP-15", "autre") in errorsOf(*valid(cantal to other).toTypedArray()))
        val v2 = file(*valid().toTypedArray()).replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        assertTrue(ValidationError.UnsupportedSchema(2) in (GeoCatalogLoader.load(v2) as LoadResult.Invalid).errors)
    }

    @Test
    fun populationNeedsItsYear_andIsNotForCapitals() {
        val withoutYear = cantal.replace("\"sourceId\"", "\"population\":144000,\"sourceId\"")
        assertTrue(ValidationError.InvalidPopulation("DEP-15") in errorsOf(*valid(cantal to withoutYear).toTypedArray()))
        val ok = cantal.replace("\"sourceId\"", "\"population\":144000,\"populationYear\":2023,\"sourceId\"")
        assertTrue(GeoCatalogLoader.load(file(*valid(cantal to ok).toTypedArray())) is LoadResult.Success)
        val onCapital = aurillac.replace("\"sourceId\"", "\"population\":25000,\"populationYear\":2023,\"sourceId\"")
        assertTrue(ValidationError.InvalidPopulation("CL-15") in errorsOf(*valid(aurillac to onCapital).toTypedArray()))
    }

    @Test
    fun areaIsForDepartmentsOnly_andPositive() {
        val negative = rhone.replace("3249", "-1")
        assertTrue(ValidationError.InvalidArea("DEP-69") in errorsOf(*valid(rhone to negative).toTypedArray()))
    }

    private val loire = """{"id":"RV-LOIRE","type":"river","name":"Loire","departmentIds":["DEP-15"],"sourceId":"src"}"""
    private val allier = """{"id":"RV-ALLIER","type":"river","name":"Allier","parentId":"RV-LOIRE","departmentIds":["DEP-15"],"sourceId":"src"}"""

    @Test
    fun rivers_parentIsOptional_butMustBeARiver() {
        val catalog = (GeoCatalogLoader.load(file(*(valid() + loire + allier).toTypedArray())) as LoadResult.Success).catalog
        assertEquals(listOf("RV-ALLIER"), catalog.tributariesOf("RV-LOIRE").map { it.id })
        assertEquals("RV-LOIRE", catalog.mouthOf(catalog["RV-ALLIER"]!!).id)
        val intoDepartment = allier.replace("\"RV-LOIRE\"", "\"DEP-15\"")
        assertTrue(ValidationError.InvalidParent("RV-ALLIER", "DEP-15") in errorsOf(*(valid() + loire + intoDepartment).toTypedArray()))
    }

    @Test
    fun rivers_haveNoCodeNorCapital_andCrossRealDepartments() {
        val withCode = loire.replace("\"name\":\"Loire\"", "\"name\":\"Loire\",\"code\":\"42\"")
        assertTrue(ValidationError.InvalidCode("RV-LOIRE", "42") in errorsOf(*(valid() + withCode).toTypedArray()))
        val crossesRegion = loire.replace("DEP-15", "REG-ARA")
        assertTrue(ValidationError.InvalidDepartmentRef("RV-LOIRE", "REG-ARA") in errorsOf(*(valid() + crossesRegion).toTypedArray()))
        // Seul un cours d'eau a des départements traversés.
        val departmentWithList = cantal.replace("\"sourceId\"", "\"departmentIds\":[\"DEP-69\"],\"sourceId\"")
        assertTrue(ValidationError.InvalidDepartmentRef("DEP-15", "DEP-69") in errorsOf(*valid(cantal to departmentWithList).toTypedArray()))
    }

    @Test
    fun rivers_cannotFlowIntoThemselves() {
        val loop = loire.replace("\"name\":\"Loire\"", "\"name\":\"Loire\",\"parentId\":\"RV-ALLIER\"")
        val errors = errorsOf(*(valid() + loop + allier).toTypedArray())
        assertTrue(errors.toString(), ValidationError.ParentCycle("RV-LOIRE") in errors)
    }
}
