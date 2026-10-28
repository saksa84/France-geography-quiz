package io.github.saksa84.francemapquiz.data

import android.content.res.AssetManager
import io.github.saksa84.francemapquiz.map.MapGeometry

/**
 * Tout ce qu'il faut pour afficher la carte : les lieux (noms, numéros…), leurs contours et leurs fiches.
 * @param geometry carte de France (métropole et cartouches des départements d'outre-mer)
 * @param world carte du monde de la catégorie Outre-mer : départements et collectivités à leur vraie place
 */
class MapData(
    val catalog: GeoCatalog,
    val geometry: MapGeometry,
    val facts: Map<String, PlaceFacts> = emptyMap(),
    val world: MapGeometry? = null,
) {
    /** Les mêmes données, avec la carte du monde comme carte à dessiner (catégorie Outre-mer). */
    fun onWorldMap(): MapData = world?.let { MapData(catalog, it, facts, it) } ?: this
}

/** Lit les fichiers de `assets/geo`. Seule partie du package `data` qui dépend d'Android. */
class AssetGeoRepository(private val assets: AssetManager) {

    /** Lecture et analyse des fichiers : à appeler hors du thread principal. */
    fun load(): MapData {
        val catalog = when (val result = GeoCatalogLoader.load(*CATALOG_FILES.map(::read).toTypedArray())) {
            is LoadResult.Success -> result.catalog
            is LoadResult.Malformed -> error("Données géographiques illisibles : ${result.message}")
            is LoadResult.Invalid -> error("Données géographiques incohérentes : ${result.errors}")
        }
        val geometry = GEOMETRY_FILES.map { MapGeometry.parse(read(it)) }.reduce(MapGeometry::plus)
        val world = MapGeometry.parse(read(WORLD_FILE))
        val facts = when (val result = PlaceFactsLoader.load(read(FACTS_FILE), catalog)) {
            is FactsResult.Success -> result.facts
            is FactsResult.Malformed -> error("Fiches illisibles : ${result.message}")
            is FactsResult.Invalid -> error("Fiches incohérentes : ${result.errors}")
        }
        return MapData(catalog, geometry, facts, world)
    }

    private fun read(name: String): String = assets.open("geo/$name").bufferedReader().use { it.readText() }

    companion object {
        val CATALOG_FILES = listOf(
            "regions.json", "departements.json", "chefs-lieux.json", "cours-eau.json", "montagnes.json", "parcs.json", "outre-mer.json",
        )

        /**
         * Contours des départements, points des chefs-lieux, cadres et fond de carte (tools/build_geo.py),
         * puis tracés des cours d'eau (tools/build_rivers.py), massifs et sommets (tools/build_mountains.py),
         * parcs nationaux (tools/build_parks.py), dans la même projection.
         */
        val GEOMETRY_FILES = listOf(
            "map.geometry.json", "cours-eau.geometry.json", "montagnes.geometry.json", "parcs.geometry.json",
        )

        /** Carte du monde de la catégorie Outre-mer (tools/build_overseas.py), à part : autre projection. */
        const val WORLD_FILE = "monde.geometry.json"

        /** Fiches « En savoir plus », indexées par identifiant. */
        const val FACTS_FILE = "facts.json"
    }
}
