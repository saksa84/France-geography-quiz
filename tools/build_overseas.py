"""Produit les données de l'outre-mer (version 3) : collectivités d'outre-mer et carte du monde.

Usage (depuis la racine du projet, après tools/download_sources.py et tools/build_geo.py) :
    python -X utf8 tools/build_overseas.py

Entrées (tools/raw/, voir docs/data-sources.md) :
- v_comer_2026.csv : INSEE, COG 2026, collectivités d'outre-mer (numéro, nom) ;
- v_departement_2026.csv, mairies.geojson.gz : chefs-lieux des départements d'outre-mer ;
- departements-100m.geojson.gz : IGN ADMIN EXPRESS COG 2026 généralisé par Etalab, qui contient aussi 975 à 989 ;
- wikidata/*.json : chaque collectivité (propriété « capitale », P36) et son chef-lieu (coordonnées), révisions fixées ;
- ne_10m_admin_0_countries.geojson : Natural Earth, pays du monde (fond de carte).

Sorties (app/src/main/assets/geo/) :
- outre-mer.json : les 6 collectivités (numéro, chef-lieu, statut) et leurs chefs-lieux ;
- monde.geometry.json : carte du monde de la catégorie Outre-mer (projection de Mercator), avec les
  5 départements et les 6 collectivités d'outre-mer à leur vraie place, et la métropole en repère.

Seule la bibliothèque standard de Python est utilisée.
"""

import gzip
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_mountains import SOURCES as MOUNTAIN_SOURCES  # noqa: E402
from build_geo import (GRID_KM, OUT, OVERSEAS_CODES, RAW, SOURCES as GEO_SOURCES, clip_ring, department_units,  # noqa: E402
                       douglas_peucker, label_point, normalized, place_inside, read_csv, ring_area)

WIKIDATA_DIR = RAW / "wikidata"
CONSTITUTION = "https://www.conseil-constitutionnel.fr/le-bloc-de-constitutionnalite/texte-integral-de-la-constitution-du-4-octobre-1958-en-vigueur"
WIKIDATA_SOURCE = MOUNTAIN_SOURCES[1]  # même déclaration dans tous les fichiers : le chargement le vérifie

ARTICLE_74 = "Collectivité d'outre-mer régie par l'article 74 de la Constitution (article 72-3)."
# Numéro, élément Wikidata de la collectivité et sa révision, chef-lieu (nom, alias, élément, révision), statut.
COLLECTIVITIES = [
    ("975", "Q34617", 2558484892, "Saint-Pierre", [], "Q185678", 2480701083, ARTICLE_74),
    ("977", "Q25362", 2558486701, "Gustavia", [], "Q34112", 2523334292, ARTICLE_74),
    ("978", "Q126125", 2561355159, "Marigot", [], "Q200605", 2517743197, ARTICLE_74),
    ("986", "Q35555", 2558481472, "Mata-Utu", ["Mata Utu"], "Q31002", 2561097348, ARTICLE_74),
    ("987", "Q30971", 2560615927, "Papeete", [], "Q130800", 2551136487, ARTICLE_74),
    ("988", "Q33788", 2561458825, "Nouméa", [], "Q9733", 2559881113,
     "Collectivité à statut particulier, régie par le titre XIII de la Constitution, et non par l'article 74 (article 72-3)."),
]

# --- Carte du monde ---
# Mercator : conforme, une île zoomée garde sa vraie forme. Unité : 100 m à l'équateur (×1,4 à la latitude de Paris).
EARTH_KM = 6378.137
LAT_MIN, LAT_MAX = -58.0, 76.0  # sans l'Antarctique ni le Grand Nord, inutiles ici
TOLERANCE = 1.0  # 100 m : les îles restent reconnaissables au zoom maximal
MIN_RING = 1.0  # 1 unité² (0,01 km² à l'équateur) : on garde les petits atolls de Polynésie
ZONE_KM = 3  # marge de la zone de toucher autour d'un territoire : Saint-Martin et Saint-Barthélemy ne sont qu'à 7 km l'un de l'autre en longitude
MAX_ZOOM = 1000  # par rapport à la vue du monde entier : Saint-Barthélemy fait alors un quart d'écran
# Pays du monde : Natural Earth sans la France (dessinée à part) ni les collectivités (contours IGN).
FRENCH = {"FRA", "SPM", "BLM", "MAF", "WLF", "PYF", "NCL"}


def mercator(lon, lat):
    """Mercator sphérique, y vers le bas, en unités de carte."""
    lat = max(min(lat, 85.0), -85.0)
    x = EARTH_KM * math.radians(lon)
    y = EARTH_KM * math.log(math.tan(math.pi / 4 + math.radians(lat) / 2))
    return x / GRID_KM, -y / GRID_KM


def wikidata_files():
    """Fichiers à télécharger (download_sources.py) : chaque collectivité et son chef-lieu, à leur révision."""
    files = {}
    for _, item, rev, _, _, capital, capital_rev, _ in COLLECTIVITIES:
        files[f"wikidata/{item}-{rev}.json"] = f"https://www.wikidata.org/wiki/Special:EntityData/{item}.json?revision={rev}"
        files[f"wikidata/{capital}-{capital_rev}.json"] = f"https://www.wikidata.org/wiki/Special:EntityData/{capital}.json?revision={capital_rev}"
    return files


def entity(item, rev):
    return json.loads((WIKIDATA_DIR / f"{item}-{rev}.json").read_text(encoding="utf-8"))["entities"][item]


def territory_polygons(polygons):
    """Contours IGN d'un territoire, projetés et simplifiés ; les plus petits îlots sont écartés."""
    result = []
    for polygon in polygons:
        rings = []
        for ring in polygon:
            projected = [mercator(lon / 1e7, lat / 1e7) for lon, lat in ring]
            simple = douglas_peucker(projected + projected[:1], TOLERANCE)[:-1]
            if len(simple) >= 3 and ring_area(simple) >= MIN_RING:
                rings.append(simple)
        if rings:
            result.append(rings)
    return result


def world_polygons(near):
    """
    Fond de carte : pays du monde (Natural Earth), et la métropole à part, pour la reconnaître.
    Seules les terres proches d'un territoire (`near` : rectangles en unités de carte) restent précises,
    car on ne zoome que là (Sint Maarten, la Dominique, le Suriname…) ; ailleurs, le trait est grossier.
    """
    countries = json.loads((RAW / "ne_10m_admin_0_countries.geojson").read_text(encoding="utf-8"))
    y_top, y_bottom = mercator(0, LAT_MAX)[1], mercator(0, LAT_MIN)[1]
    context, metro = [], []
    for feature in countries["features"]:
        code = feature["properties"]["ADM0_A3"]
        if code in FRENCH - {"FRA"} or code == "ATA":
            continue
        geometry = feature["geometry"]
        raw = [geometry["coordinates"]] if geometry["type"] == "Polygon" else geometry["coordinates"]
        for polygon in raw:
            outer = polygon[0]
            lons = [p[0] for p in outer]
            lats = [p[1] for p in outer]
            in_europe = -6 < min(lons) and max(lons) < 10 and 41 < min(lats) and max(lats) < 52
            if code == "FRA" and not in_europe:
                continue  # départements d'outre-mer : contours IGN
            projected = [mercator(lon, lat) for lon, lat in outer[:-1]]
            xs = [x for x, _ in projected]
            ys = [y for _, y in projected]
            extent = max(max(xs) - min(xs), max(ys) - min(ys))
            close = any(min(xs) < b[2] and max(xs) > b[0] and min(ys) < b[3] and max(ys) > b[1] for b in near)
            tolerance = min(max(extent / 80, 2.0), 60.0) if close or code == "FRA" else max(extent / 30, 150.0)
            clipped = clip_ring(douglas_peucker(projected + projected[:1], tolerance)[:-1], -1e9, y_top, 1e9, y_bottom)
            ring = []
            for x, y in clipped:
                q = (round(x), round(y))
                if not ring or ring[-1] != q:
                    ring.append(q)
            if len(ring) >= 3 and ring_area(ring) * GRID_KM ** 2 >= (1 if close or code == "FRA" else 300):
                (metro if code == "FRA" else context).append([ring])
    return context, metro


def flat(polygons, digits=None):
    return [[[round(c, digits) if digits else round(c) for p in r for c in p] for r in poly] for poly in polygons]


def zone(polygons, margin):
    xs = [x for poly in polygons for r in poly for x, _ in r]
    ys = [y for poly in polygons for r in poly for _, y in r]
    return [round(min(xs) - margin), round(min(ys) - margin), round(max(xs) + margin), round(max(ys) + margin)]


def main() -> int:
    names = {r["COMER"]: r["LIBELLE"] for r in read_csv("v_comer_2026.csv")}
    departments = {d["DEP"]: d for d in read_csv("v_departement_2026.csv")}
    codes = [c[0] for c in COLLECTIVITIES]
    units = department_units(set(OVERSEAS_CODES) | set(codes))
    missing = [c for c in codes if c not in units or c not in names] + [c for c in OVERSEAS_CODES if c not in units]
    if missing:
        print(f"ERREUR : territoires sans contour ou sans nom : {missing}")
        return 1
    mairies = {f["properties"]["commune"]: f["geometry"]["coordinates"]
               for f in json.load(gzip.open(RAW / "mairies.geojson.gz", "rt", encoding="utf-8"))["features"]}

    features, entities = [], []

    def add_territory(feature_id, capital_id, polygons, lon, lat, label):
        # Zone de toucher : le territoire et ses eaux, pour la Polynésie dont les îles sont des points à l'échelle du monde.
        margin = ZONE_KM / GRID_KM / math.cos(math.radians(lat))
        (px, py), moved = place_inside(polygons, *mercator(lon, lat), margin=0.5)
        features.append({"id": feature_id, "label": label_point(polygons), "zone": zone(polygons, margin), "polygons": flat(polygons, 1)})
        features.append({"id": capital_id, "kind": "point", "point": [round(px, 1), round(py, 1)]})
        islands = len(polygons)
        print(f"  {feature_id:<8} {label:<26} {islands:3d} îles" + (f", chef-lieu rentré de {moved * GRID_KM:.1f} km" if moved else ""))

    # Départements d'outre-mer : mêmes identifiants que dans la carte de France (DEP-971, CL-971…).
    for code in OVERSEAS_CODES:
        lon, lat = mairies[departments[code]["CHEFLIEU"]]
        add_territory(f"DEP-{code}", f"CL-{code}", territory_polygons(units[code]), lon, lat, departments[code]["LIBELLE"])

    for code, item, rev, capital_name, capital_aliases, capital, capital_rev, status in COLLECTIVITIES:
        # Le chef-lieu doit être la « capitale » (P36) de la collectivité dans Wikidata.
        claims = [c["mainsnak"]["datavalue"]["value"]["id"] for c in entity(item, rev)["claims"].get("P36", []) if c["rank"] != "deprecated"]
        if claims != [capital]:
            print(f"ERREUR : {code} a pour capitale {claims} dans Wikidata, pas {capital}")
            return 1
        coord = entity(capital, capital_rev)["claims"]["P625"][0]["mainsnak"]["datavalue"]["value"]
        add_territory(f"COM-{code}", f"CL-{code}", territory_polygons(units[code]), coord["longitude"], coord["latitude"], names[code])
        entities.append({"id": f"COM-{code}", "type": "collectivity", "name": names[code], "code": code, "capitalId": f"CL-{code}",
                         "overseas": True, "note": status, "noteSource": CONSTITUTION, "sourceId": GEO_SOURCES[0]["id"]})
        entities.append({"id": f"CL-{code}", "type": "prefecture", "name": capital_name, "aliases": capital_aliases, "parentId": f"COM-{code}",
                         "wikidata": f"{capital}@{capital_rev}", "sourceId": WIKIDATA_SOURCE["id"]})

    seen = {}
    for e in entities:
        for n in [e["name"]] + e.get("aliases", []):
            k = (e["type"], normalized(n))
            if seen.get(k, e["id"]) != e["id"]:
                print(f"ERREUR : « {n} » se confond avec {seen[k]}")
                return 1
            seen[k] = e["id"]

    text = (json.dumps({"schemaVersion": 1, "sources": [GEO_SOURCES[0], WIKIDATA_SOURCE]}, ensure_ascii=False)[:-1] + ',\n"entities": [\n'
            + ",\n".join(json.dumps(e, ensure_ascii=False, separators=(", ", ": ")) for e in entities) + "\n]}\n")
    (OUT / "outre-mer.json").write_text(text, encoding="utf-8", newline="\n")

    # Zone de précision : 600 km autour de chaque territoire.
    near = [[f["zone"][0] - 6000, f["zone"][1] - 6000, f["zone"][2] + 6000, f["zone"][3] + 6000] for f in features if "zone" in f]
    context, metro = world_polygons(near)
    x0, y0 = mercator(-180, LAT_MAX)
    x1, y1 = mercator(180, LAT_MIN)
    header = {"schemaVersion": 1, "sourceId": GEO_SOURCES[1]["id"],
              "projection": {"world": f"Mercator sphérique (rayon {EARTH_KM} km), latitudes {LAT_MIN} à {LAT_MAX}"},
              "unitKm": GRID_KM, "yAxis": "down", "bounds": [round(x0), round(y0), round(x1), round(y1)], "maxZoom": MAX_ZOOM}
    context_lines = [json.dumps({"polygons": [[[c for p in r for c in p] for r in poly]]}, separators=(",", ":")) for poly in context]
    text = (json.dumps(header, ensure_ascii=False)[:-1] + ',\n"features":[\n'
            + ",\n".join(json.dumps(f, separators=(",", ":")) for f in features)
            + '\n],\n"highlight":[\n' + json.dumps({"polygons": flat(metro)}, separators=(",", ":"))
            + '\n],\n"context":[\n' + ",\n".join(context_lines) + "\n]}\n")
    (OUT / "monde.geometry.json").write_text(text, encoding="utf-8", newline="\n")
    old = OUT / "outre-mer.geometry.json"
    if old.exists():
        old.unlink()  # ancienne rangée de cartouches, remplacée par la carte du monde
    points = sum(len(r) for poly in context for r in poly)
    own = sum(len(r) // 2 for f in features for poly in f.get("polygons", []) for r in poly)
    print(f"{len(COLLECTIVITIES)} collectivités, territoires {own} sommets, {len(context)} terres de fond ({points} sommets), "
          f"monde.geometry.json {(OUT / 'monde.geometry.json').stat().st_size // 1024} Ko")
    return 0


if __name__ == "__main__":
    sys.exit(main())
