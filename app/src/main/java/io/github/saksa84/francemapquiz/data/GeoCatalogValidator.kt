package io.github.saksa84.francemapquiz.data

/** Problème détecté dans les données. Chaque cas est un type distinct pour être vérifiable dans les tests. */
sealed interface ValidationError {
    data class UnsupportedSchema(val version: Int) : ValidationError
    data class DuplicateId(val id: String) : ValidationError

    /** Deux sources portent le même identifiant mais des informations différentes. */
    data class ConflictingSource(val id: String) : ValidationError
    data class UnknownSource(val entityId: String, val sourceId: String) : ValidationError
    data class MissingParent(val entityId: String) : ValidationError
    data class UnknownParent(val entityId: String, val parentId: String) : ValidationError

    /** Le parent existe mais n'est pas d'une catégorie autorisée (département sous un département…). */
    data class InvalidParent(val entityId: String, val parentId: String) : ValidationError

    /** Nom en français : lettres latines (accents permis), chiffres, espaces, apostrophes, traits d'union. */
    data class InvalidName(val entityId: String, val name: String) : ValidationError

    /** Numéro absent ou mal formé : 01 à 95, 2A, 2B, 971 à 976 pour un département ; deux chiffres pour une région. */
    data class InvalidCode(val entityId: String, val code: String?) : ValidationError
    data class DuplicateCode(val code: String) : ValidationError

    /** Un département ou une région doit avoir un chef-lieu ; un chef-lieu n'en a pas. */
    data class MissingCapital(val entityId: String) : ValidationError

    /**
     * Le chef-lieu doit être une préfecture rattachée au département lui-même ou, pour une région,
     * à l'un de ses départements. Évite de confondre chef-lieu de département et de région.
     */
    data class InvalidCapital(val entityId: String, val capitalId: String) : ValidationError

    /** Une région sans département. */
    data class EmptyRegion(val regionId: String) : ValidationError

    /** Un département et sa région sont tous deux d'outre-mer, ou tous deux de métropole. */
    data class OverseasMismatch(val entityId: String) : ValidationError

    /** Superficie réservée aux départements, strictement positive. */
    data class InvalidArea(val entityId: String) : ValidationError

    /** Une note a toujours sa source, et une source sans note n'a pas de sens. */
    data class NoteWithoutSource(val entityId: String) : ValidationError

    /** Population strictement positive, toujours accompagnée de son année ; jamais pour un chef-lieu. */
    data class InvalidPopulation(val entityId: String) : ValidationError

    /** Départements traversés : réservés aux cours d'eau, et chacun doit être un département connu. */
    data class InvalidDepartmentRef(val entityId: String, val departmentId: String) : ValidationError

    /** Un cours d'eau ne peut pas se jeter, de proche en proche, dans lui-même. */
    data class ParentCycle(val entityId: String) : ValidationError
}

object GeoCatalogValidator {
    const val SUPPORTED_SCHEMA_VERSION = 1

    private val FRENCH_NAME = Regex("^\\p{IsLatin}[\\p{IsLatin}0-9 '\\-]*$")
    private val DEPARTMENT_CODE = Regex("^(0[1-9]|1[0-9]|2[1-9]|[3-8][0-9]|9[0-5]|2A|2B|97[1-46])$")
    private val REGION_CODE = Regex("^[0-9]{2}$")

    fun validate(files: List<GeoCatalogFile>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        for (file in files) {
            if (file.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                errors += ValidationError.UnsupportedSchema(file.schemaVersion)
            }
        }

        // Une même source peut être déclarée dans plusieurs fichiers, à condition d'être identique.
        val sources = mutableMapOf<String, DataSource>()
        for (source in files.flatMap { it.sources }) {
            val previous = sources.putIfAbsent(source.id, source)
            if (previous != null && previous != source) errors += ValidationError.ConflictingSource(source.id)
        }

        val entities = files.flatMap { it.entities }
        val seenIds = mutableSetOf<String>()
        for (entity in entities) {
            if (!seenIds.add(entity.id)) errors += ValidationError.DuplicateId(entity.id)
        }

        // Première occurrence de chaque identifiant : suffit pour vérifier les références.
        val byId = entities.associateBy { it.id }

        for (entity in entities) {
            if (entity.sourceId !in sources) {
                errors += ValidationError.UnknownSource(entity.id, entity.sourceId)
            }
            for (name in listOf(entity.name) + entity.aliases) {
                if (!FRENCH_NAME.matches(name)) errors += ValidationError.InvalidName(entity.id, name)
            }
            errors += checkCode(entity)
            errors += checkParent(entity, byId)
            errors += checkCapital(entity, byId)
            entity.areaKm2?.let { area ->
                if (entity.type != FeatureType.DEPARTMENT || area <= 0) errors += ValidationError.InvalidArea(entity.id)
            }
            val population = entity.population
            val populationOk = if (population == null) {
                entity.populationYear == null
            } else {
                entity.type in setOf(FeatureType.DEPARTMENT, FeatureType.REGION) && population > 0 && entity.populationYear in 1950..2100
            }
            if (!populationOk) errors += ValidationError.InvalidPopulation(entity.id)
            if (entity.note.isNullOrBlank() != entity.noteSource.isNullOrBlank()) errors += ValidationError.NoteWithoutSource(entity.id)
            if (entity.type == FeatureType.DEPARTMENT) {
                val region = entity.parentId?.let { byId[it] }
                if (region != null && region.overseas != entity.overseas) errors += ValidationError.OverseasMismatch(entity.id)
            }
            if (entity.type == FeatureType.REGION && entities.none { it.type == FeatureType.DEPARTMENT && it.parentId == entity.id }) {
                errors += ValidationError.EmptyRegion(entity.id)
            }
            for (departmentId in entity.departmentIds) {
                if (entity.type != FeatureType.RIVER || byId[departmentId]?.type != FeatureType.DEPARTMENT) {
                    errors += ValidationError.InvalidDepartmentRef(entity.id, departmentId)
                }
            }
            if (hasParentCycle(entity, byId)) errors += ValidationError.ParentCycle(entity.id)
        }

        // Numéros uniques dans chaque famille (le 75 d'un département n'est pas celui d'une région).
        for ((_, family) in entities.filter { it.code != null }.groupBy { it.type }) {
            family.groupBy { it.code }.filterValues { it.size > 1 }.keys.forEach { errors += ValidationError.DuplicateCode(it!!) }
        }
        return errors
    }

    private fun checkCode(entity: GeoEntity): List<ValidationError> {
        val code = entity.code
        val valid = when (entity.type) {
            FeatureType.DEPARTMENT -> code != null && DEPARTMENT_CODE.matches(code) && entity.overseas == (code.length == 3)
            FeatureType.REGION -> code != null && REGION_CODE.matches(code)
            FeatureType.PREFECTURE, FeatureType.RIVER -> code == null
        }
        return if (valid) emptyList() else listOf(ValidationError.InvalidCode(entity.id, code))
    }

    private fun hasParentCycle(entity: GeoEntity, byId: Map<String, GeoEntity>): Boolean {
        val seen = mutableSetOf(entity.id)
        var parentId = entity.parentId
        while (parentId != null) {
            if (!seen.add(parentId)) return true
            parentId = byId[parentId]?.parentId
        }
        return false
    }

    private fun checkParent(entity: GeoEntity, byId: Map<String, GeoEntity>): List<ValidationError> {
        val parentId = entity.parentId
            ?: return if (entity.type.parentRequired) listOf(ValidationError.MissingParent(entity.id)) else emptyList()
        val parent = byId[parentId] ?: return listOf(ValidationError.UnknownParent(entity.id, parentId))
        return if (parent.category in entity.type.parentCategories && parent.id != entity.id) {
            emptyList()
        } else {
            listOf(ValidationError.InvalidParent(entity.id, parentId))
        }
    }

    private fun checkCapital(entity: GeoEntity, byId: Map<String, GeoEntity>): List<ValidationError> {
        val capitalId = entity.capitalId
        if (entity.type == FeatureType.PREFECTURE || entity.type == FeatureType.RIVER) {
            return if (capitalId == null) emptyList() else listOf(ValidationError.InvalidCapital(entity.id, capitalId))
        }
        if (capitalId == null) return listOf(ValidationError.MissingCapital(entity.id))
        val capital = byId[capitalId]
        val department = capital?.parentId?.let { byId[it] }
        val valid = capital?.type == FeatureType.PREFECTURE && when (entity.type) {
            FeatureType.DEPARTMENT -> capital.parentId == entity.id
            FeatureType.REGION -> department?.parentId == entity.id
            FeatureType.PREFECTURE, FeatureType.RIVER -> false
        }
        return if (valid) emptyList() else listOf(ValidationError.InvalidCapital(entity.id, capitalId))
    }
}
