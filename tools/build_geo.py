"""Construit les données géographiques de l'application à partir des sources officielles.

Usage (depuis la racine du projet, après tools/download_sources.py) :
    python -X utf8 tools/build_geo.py

Entrées (tools/raw/, voir docs/data-sources.md) :
    v_departement_2026.csv, v_region_2026.csv, v_commune_2026.csv   INSEE, COG 2026
    departements-100m.geojson.gz, mairies.geojson.gz                Etalab / IGN ADMIN EXPRESS COG 2026
    ne_10m_admin_0_countries.geojson                                Natural Earth 5.1.2 (pays voisins)
    pop-ref-2023-ensemble.zip                                       INSEE, populations de référence 2023
    comparateur_csv.zip                                             INSEE, base du comparateur de territoires (superficies)
Sorties (app/src/main/assets/geo/) :
    regions.json        18 régions (13 de métropole, 5 d'outre-mer)
    departements.json   101 départements : numéro, nom, chef-lieu, région, superficie, population
    chefs-lieux.json    101 chefs-lieux (points rattachés à leur département)
    map.geometry.json   contours des départements, points des chefs-lieux, cadres et fond de carte

Étapes :
    1. lecture du COG : départements, régions, chef-lieu officiel (code commune) ;
    2. projection : métropole en Lambert-93 (EPSG:2154, ellipsoïde GRS80) ; chaque département
       d'outre-mer dans son propre cartouche, avec une projection locale et sa propre échelle ;
    3. simplification de Douglas-Peucker *par frontière partagée* (reprise de China Map Quiz) :
       une frontière commune à deux départements est simplifiée une seule fois, sans trou ni
       chevauchement ; les petites îles (≥ 1 km²) sont gardées ;
    4. chefs-lieux : position de la mairie, déplacée vers l'intérieur si la simplification du
       littoral l'a laissée dehors (le script le signale) ;
    5. vérifications et écriture.

Uniquement la bibliothèque standard de Python : rien à installer.
"""

import csv
import gzip
import io
import json
import math
import re
import sys
import unicodedata
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "tools" / "raw"
OUT = ROOT / "app" / "src" / "main" / "assets" / "geo"

INSEE_SOURCE = "insee-cog-2026"
IGN_SOURCE = "ign-admin-express-cog-2026"
NE_SOURCE = "natural-earth-5.1.2"
POPULATION_YEAR = 2023  # populations de référence 2023, en vigueur au 1er janvier 2026

SOURCES = [
    {
        "id": INSEE_SOURCE,
        "name": "INSEE, Code officiel géographique au 1er janvier 2026",
        "license": "Licence Ouverte Etalab 2.0",
        "url": "https://www.insee.fr/fr/information/8740222",
        "version": "2026",
    },
    {
        "id": IGN_SOURCE,
        "name": "IGN ADMIN EXPRESS COG 2026, contours généralisés à 100 m par Etalab",
        "license": "Licence Ouverte Etalab 2.0",
        "url": "https://etalab-datasets.geo.data.gouv.fr/contours-administratifs/2026/",
        "version": "2026",
    },
    {
        "id": "insee-populations-2023",
        "name": "INSEE, populations de référence 2023 (population municipale), en vigueur au 1er janvier 2026",
        "license": "Licence Ouverte Etalab 2.0",
        "url": "https://www.insee.fr/fr/statistiques/8680726",
        "version": "2023",
    },
    {
        "id": "insee-comparateur-2026",
        "name": "INSEE, base du comparateur de territoires (superficie)",
        "license": "Licence Ouverte Etalab 2.0",
        "url": "https://www.insee.fr/fr/statistiques/2521169",
        "version": "2026",
    },
]

# Identifiants stables des régions, indépendants des noms (codes INSEE en valeur).
REGION_IDS = {
    "11": "REG-IDF", "24": "REG-CVL", "27": "REG-BFC", "28": "REG-NOR", "32": "REG-HDF",
    "44": "REG-GES", "52": "REG-PDL", "53": "REG-BRE", "75": "REG-NAQ", "76": "REG-OCC",
    "84": "REG-ARA", "93": "REG-PAC", "94": "REG-COR",
    "01": "REG-GUA", "02": "REG-MTQ", "03": "REG-GUY", "04": "REG-REU", "06": "REG-MAY",
}
OVERSEAS_CODES = ["971", "972", "973", "974", "976"]  # ordre des cartouches, de gauche à droite

# Alias acceptés en plus des abréviations Saint/Sainte (ajoutées automatiquement).
# Seuls les cas tranchés vont ici ; les propositions en attente sont dans docs/plan-initial.md.
EXTRA_ALIASES = {
    "DEP-974": ["Réunion"],
    "REG-REU": ["Réunion"],
    "REG-PAC": ["PACA", "Région Sud"],  # sigle courant ; « Région Sud » : nom d'usage adopté par la région en 2017
    "CL-91": ["Évry"],  # nom de la commune avant sa fusion avec Courcouronnes (2019)
}

# Notes administratives affichées dans la fiche, vérifiées sur Légifrance le 27 octobre 2026
# (décision du 27 octobre 2026, docs/plan-initial.md). Chaque note a sa source.
NOTES = {
    "DEP-2A": ('Depuis le 1er janvier 2018, la Collectivité de Corse remplace la collectivité territoriale de Corse et les départements de Corse-du-Sud et de Haute-Corse (loi du 7 août 2015) ; les numéros 2A et 2B demeurent.', 'https://www.legifrance.gouv.fr/jorf/article_jo/JORFARTI000030986213'),
    "DEP-2B": ('Depuis le 1er janvier 2018, la Collectivité de Corse remplace la collectivité territoriale de Corse et les départements de Corse-du-Sud et de Haute-Corse (loi du 7 août 2015) ; les numéros 2A et 2B demeurent.', 'https://www.legifrance.gouv.fr/jorf/article_jo/JORFARTI000030986213'),
    "DEP-67": ("Depuis le 1er janvier 2021, la Collectivité européenne d'Alsace regroupe les départements du Bas-Rhin et du Haut-Rhin (loi du 2 août 2019) ; les numéros 67 et 68 demeurent.", 'https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000038872957'),
    "DEP-68": ("Depuis le 1er janvier 2021, la Collectivité européenne d'Alsace regroupe les départements du Bas-Rhin et du Haut-Rhin (loi du 2 août 2019) ; les numéros 67 et 68 demeurent.", 'https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000038872957'),
    "DEP-69": ("Depuis le 1er janvier 2015, la Métropole de Lyon exerce sur son territoire les compétences du département (loi du 27 janvier 2014) ; le numéro 69 couvre les deux.",
               "https://www.legifrance.gouv.fr/jorf/article_jo/JORFARTI000028526403"),
    "DEP-75": ("Depuis le 1er janvier 2019, la Ville de Paris remplace la commune et le département de Paris (loi du 28 février 2017).",
               "https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000034115674"),
    "DEP-972": ("Depuis décembre 2015, la collectivité territoriale de Martinique exerce les compétences du département et de la région (loi du 27 juillet 2011).",
                "https://legifrance.gouv.fr/affichTexte.do?categorieLien=id&cidTexte=JORFTEXT000024403725"),
    "DEP-973": ("Depuis décembre 2015, la collectivité territoriale de Guyane exerce les compétences du département et de la région (loi du 27 juillet 2011).",
                "https://legifrance.gouv.fr/affichTexte.do?categorieLien=id&cidTexte=JORFTEXT000024403725"),
    "DEP-976": ("Mayotte est un département depuis le 31 mars 2011 (loi organique du 7 décembre 2010).",
                "https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000023174540"),
}

# --- Projection -------------------------------------------------------------------------

GRID_KM = 0.1  # unité de la carte : 100 m en métropole

# Lambert-93 : conique conforme sécante, ellipsoïde GRS80 (paramètres officiels de l'IGN).
A = 6378137.0
E = 0.0818191910428158
LAMBERT93 = {"lon0": 3.0, "lat0": 46.5, "lat1": 44.0, "lat2": 49.0, "x0": 700000.0, "y0": 6600000.0}


def _m(phi):
    return math.cos(phi) / math.sqrt(1 - (E * math.sin(phi)) ** 2)


def _t(phi):
    return math.tan(math.pi / 4 - phi / 2) / ((1 - E * math.sin(phi)) / (1 + E * math.sin(phi))) ** (E / 2)


_p1, _p2, _p0 = (math.radians(LAMBERT93[k]) for k in ("lat1", "lat2", "lat0"))
_N = (math.log(_m(_p1)) - math.log(_m(_p2))) / (math.log(_t(_p1)) - math.log(_t(_p2)))
_F = _m(_p1) / (_N * _t(_p1) ** _N)
_RHO0 = A * _F * _t(_p0) ** _N


def lambert93(lon, lat):
    """Longitude, latitude (degrés) -> x, y Lambert-93 en km (y vers le nord)."""
    rho = A * _F * _t(math.radians(lat)) ** _N
    theta = _N * math.radians(lon - LAMBERT93["lon0"])
    return (LAMBERT93["x0"] + rho * math.sin(theta)) / 1000, (LAMBERT93["y0"] + _RHO0 - rho * math.cos(theta)) / 1000


def local_projection(lon_c, lat_c):
    """Projection équirectangulaire centrée sur un petit territoire (outre-mer), en km : écart < 0,5 %."""
    k = math.cos(math.radians(lat_c))
    r = 6371.0088
    return lambda lon, lat: (r * math.radians(lon - lon_c) * k, r * math.radians(lat - lat_c))


# --- Lecture ----------------------------------------------------------------------------

def read_csv(name):
    with (RAW / name).open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def key(lon, lat):
    """Identité d'un sommet : deux départements voisins partagent exactement les mêmes clés."""
    return round(lon * 1e7), round(lat * 1e7)


def to_polygons(geometry):
    """GeoJSON -> liste de polygones ; polygone = liste d'anneaux ; anneau = liste de clés (non fermé)."""
    raw = [geometry["coordinates"]] if geometry["type"] == "Polygon" else geometry["coordinates"]
    polygons = []
    for polygon in raw:
        rings = []
        for ring in polygon:
            keys = []
            for lon, lat in ring:
                k = key(lon, lat)
                if not keys or keys[-1] != k:
                    keys.append(k)
            if len(keys) > 1 and keys[0] == keys[-1]:
                keys.pop()
            if len(keys) >= 3:
                rings.append(keys)
        if rings:
            polygons.append(rings)
    return polygons


# --- Simplification respectant les frontières partagées (reprise de China Map Quiz) ------

def find_junctions(all_rings):
    """Sommets où plus de deux segments distincts se rencontrent : début ou fin d'une frontière commune."""
    neighbours = defaultdict(set)
    for ring in all_rings:
        n = len(ring)
        for i, p in enumerate(ring):
            neighbours[p].add(ring[i - 1])
            neighbours[p].add(ring[(i + 1) % n])
    return {p for p, s in neighbours.items() if len(s) > 2}


def split_into_arcs(ring, junctions):
    """Découpe un anneau en arcs allant d'une jonction à la suivante (extrémités incluses)."""
    cuts = [i for i, p in enumerate(ring) if p in junctions]
    if not cuts:
        start = ring.index(min(ring))
        rotated = ring[start:] + ring[:start]
        return [rotated + [rotated[0]]]
    rotated = ring[cuts[0]:] + ring[:cuts[0]]
    arcs, current = [], [rotated[0]]
    for p in rotated[1:]:
        current.append(p)
        if p in junctions:
            arcs.append(current)
            current = [p]
    current.append(rotated[0])
    arcs.append(current)
    return arcs


def douglas_peucker(points, tolerance):
    """Garde les extrémités et les sommets qui s'écartent de plus de `tolerance` de la corde."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        (x1, y1), (x2, y2) = points[first], points[last]
        dx, dy = x2 - x1, y2 - y1
        length = math.hypot(dx, dy)
        best, best_i = -1.0, -1
        for i in range(first + 1, last):
            px, py = points[i]
            d = math.hypot(px - x1, py - y1) if length == 0 else abs(dy * px - dx * py + x2 * y1 - y2 * x1) / length
            if d > best:
                best, best_i = d, i
        if best > tolerance:
            keep[best_i] = True
            stack.append((first, best_i))
            stack.append((best_i, last))
    return [p for p, k in zip(points, keep) if k]


def ring_area(points):
    return abs(sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(points, points[1:] + points[:1]))) / 2


def simplify_units(units, frames):
    """Simplifie et projette chaque département dans son cadre ; renvoie les anneaux en unités de carte."""
    all_rings = [ring for polygons in units.values() for polygon in polygons for ring in polygon]
    junctions = find_junctions(all_rings)
    cache = {}
    output = {}
    for code, polygons in units.items():
        frame = frames[code]

        def simplified_arc(arc):
            t = tuple(arc)
            canonical = min(t, t[::-1])
            if canonical not in cache:
                projected = [frame["to_map"](p[0] / 1e7, p[1] / 1e7) for p in canonical]
                cache[canonical] = douglas_peucker(projected, frame["tolerance"])
            result = cache[canonical]
            return result if t == canonical else result[::-1]

        kept = []
        for polygon in polygons:
            rings = []
            for ring_index, ring in enumerate(polygon):
                points = []
                for arc in split_into_arcs(ring, junctions):
                    points.extend(simplified_arc(arc)[:-1])
                quantized = []
                for x, y in points:
                    q = (round(x), round(y))
                    if not quantized or quantized[-1] != q:
                        quantized.append(q)
                if len(quantized) > 1 and quantized[0] == quantized[-1]:
                    quantized.pop()
                if len(quantized) >= 3:
                    rings.append(quantized)
                elif ring_index == 0:
                    break
            if rings and len(rings[0]) >= 3:
                kept.append(rings)
        # Îles : seuil exprimé en superficie réelle, quel que soit le zoom du cartouche.
        unit_km2 = (GRID_KM / frame["scale"]) ** 2
        areas = [ring_area(p[0]) * unit_km2 for p in kept]
        largest = max(range(len(kept)), key=lambda i: areas[i])
        output[code] = [p for i, p in enumerate(kept) if i == largest or areas[i] >= 1.0]
    return output


# --- Points d'étiquette et tests de position --------------------------------------------

def point_in_rings(rings, x, y):
    inside = False
    for ring in rings:
        j = len(ring) - 1
        for i in range(len(ring)):
            (xi, yi), (xj, yj) = ring[i], ring[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                inside = not inside
            j = i
    return inside


def in_unit(polygons, x, y):
    return any(point_in_rings(p, x, y) for p in polygons)


def distance_to_rings(rings, x, y):
    best = float("inf")
    for ring in rings:
        for (ax, ay), (bx, by) in zip(ring, ring[1:] + ring[:1]):
            dx, dy = bx - ax, by - ay
            length_sq = dx * dx + dy * dy
            t = 0.0 if length_sq == 0 else max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / length_sq))
            best = min(best, math.hypot(x - ax - t * dx, y - ay - t * dy))
    return best


def label_point(polygons, grid=24):
    """[x, y, distance au bord] : point intérieur de la plus grande partie, le plus loin possible des bords."""
    largest = max(polygons, key=lambda p: ring_area(p[0]))
    xs = [x for x, _ in largest[0]]
    ys = [y for _, y in largest[0]]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    best = ((x0 + x1) / 2, (y0 + y1) / 2, -1.0)
    for _ in range(3):
        step_x, step_y = (x1 - x0) / grid, (y1 - y0) / grid
        for i in range(grid + 1):
            for j in range(grid + 1):
                x, y = x0 + i * step_x, y0 + j * step_y
                if point_in_rings(largest, x, y):
                    d = distance_to_rings(largest, x, y)
                    if d > best[2]:
                        best = (x, y, d)
        cx, cy = best[0], best[1]
        x0, x1, y0, y1 = cx - 2 * step_x, cx + 2 * step_x, cy - 2 * step_y, cy + 2 * step_y
    return [round(best[0]), round(best[1]), round(best[2])]


def place_inside(polygons, x, y, margin):
    """Un chef-lieu côtier peut tomber en mer après simplification du littoral : on prend le point
    le plus proche qui soit à l'intérieur, à au moins `margin` du bord (recherche sur des cercles)."""
    rings = [r for p in polygons for r in p]

    def ok(px, py):
        return in_unit(polygons, px, py) and distance_to_rings(rings, px, py) >= margin

    if ok(x, y):
        return (x, y), 0.0
    for step in range(1, 400):
        radius = step * 0.5
        for k in range(48):
            angle = 2 * math.pi * k / 48
            px, py = x + radius * math.cos(angle), y + radius * math.sin(angle)
            if ok(px, py):
                return (px, py), radius
    raise SystemExit(f"ERREUR : aucun point intérieur près de ({x:.0f}, {y:.0f})")


# --- Fond de carte : pays voisins, découpés au cadre de la métropole ----------------------

def clip_ring(ring, x0, y0, x1, y1):
    """Découpage de Sutherland-Hodgman d'un anneau par un rectangle."""
    def clip(points, inside, cross):
        out = []
        for i, p in enumerate(points):
            q = points[i - 1]
            if inside(p):
                if not inside(q):
                    out.append(cross(q, p))
                out.append(p)
            elif inside(q):
                out.append(cross(q, p))
        return out

    def at_x(xc):
        return lambda a, b: (xc, a[1] + (b[1] - a[1]) * (xc - a[0]) / (b[0] - a[0]))

    def at_y(yc):
        return lambda a, b: (a[0] + (b[0] - a[0]) * (yc - a[1]) / (b[1] - a[1]), yc)

    pts = ring
    for inside, cross in (
        (lambda p: p[0] >= x0, at_x(x0)), (lambda p: p[0] <= x1, at_x(x1)),
        (lambda p: p[1] >= y0, at_y(y0)), (lambda p: p[1] <= y1, at_y(y1)),
    ):
        if not pts:
            break
        pts = clip(pts, inside, cross)
    return pts


def context_polygons(to_map, bounds):
    """Pays voisins de la métropole (Natural Earth), simplifiés et découpés au cadre."""
    countries = json.loads((RAW / "ne_10m_admin_0_countries.geojson").read_text(encoding="utf-8"))
    x0, y0, x1, y1 = bounds
    result = []
    for feature in countries["features"]:
        if feature["properties"]["ADM0_A3"] == "FRA":
            continue
        geometry = feature["geometry"]
        raw = [geometry["coordinates"]] if geometry["type"] == "Polygon" else geometry["coordinates"]
        for polygon in raw:
            outer = polygon[0]
            lons = [p[0] for p in outer]
            lats = [p[1] for p in outer]
            if max(lons) < -8 or min(lons) > 12 or max(lats) < 40 or min(lats) > 53:
                continue
            projected = [to_map(lon, lat) for lon, lat in outer[:-1]]
            clipped = clip_ring(douglas_peucker(projected + projected[:1], 10)[:-1], x0, y0, x1, y1)
            ring = []
            for x, y in clipped:
                q = (round(x), round(y))
                if not ring or ring[-1] != q:
                    ring.append(q)
            if len(ring) >= 3 and ring_area(ring) * GRID_KM ** 2 >= 5:
                result.append([ring])
    return result


# --- Noms et alias ------------------------------------------------------------------------

def saint_aliases(name):
    """« Saint-Brieuc » -> « St-Brieuc » ; « Seine-Saint-Denis » -> « Seine-St-Denis » (abréviations usuelles)."""
    short = re.sub(r"\bSainte\b", "Ste", re.sub(r"\bSaint\b", "St", name))
    return [short] if short != name else []


def read_populations():
    """Population municipale par département et par région (codes INSEE). Mayotte n'y figure pas."""
    with zipfile.ZipFile(RAW / "pop-ref-2023-ensemble.zip") as z:
        def table(name):
            return list(csv.DictReader(io.StringIO(z.read(name).decode("utf-8-sig")), delimiter=";"))
        departments = {r["DEP"]: int(r["PMUN"]) for r in table("donnees_departements.csv")}
        regions = {r["REG"]: int(r["PMUN"]) for r in table("donnees_regions.csv")}
    return departments, regions


def read_areas():
    """Superficie officielle des départements (km², mesure « SUP » du comparateur). Mayotte n'y figure pas."""
    areas = {}
    with zipfile.ZipFile(RAW / "comparateur_csv.zip") as z, z.open("comparateur.csv") as f:
        for line in io.TextIOWrapper(f, encoding="utf-8-sig"):
            p = line.rstrip().split(";")
            if len(p) > 6 and p[1] == "DEP" and p[5] == "SUP":
                areas[p[2]] = float(p[6])
    return areas


def department_order(code):
    """Ordre officiel des numéros : 2A et 2B à la place de l'ancien 20, l'outre-mer à la fin."""
    return {"2A": 20.1, "2B": 20.2}.get(code) or float(code)


def normalized(text):
    stripped = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")
    return "".join(c for c in stripped.lower() if c.isalnum())


def entity_line(entity):
    return json.dumps(entity, ensure_ascii=False, separators=(", ", ": "))


def write_catalog(name, entities, sources):
    header = json.dumps({"schemaVersion": 1, "sources": sources}, ensure_ascii=False, indent=2)[:-2]
    text = header + ',\n  "entities": [\n' + ",\n".join("    " + entity_line(e) for e in entities) + "\n  ]\n}\n"
    (OUT / name).write_text(text, encoding="utf-8", newline="\n")


# --- Programme principal ------------------------------------------------------------------

def main() -> int:
    communes = {r["COM"]: r for r in read_csv("v_commune_2026.csv") if r["TYPECOM"] == "COM"}
    departments = read_csv("v_departement_2026.csv")
    regions = read_csv("v_region_2026.csv")
    if len(departments) != 101 or len(regions) != 18:
        print(f"ERREUR : {len(departments)} départements et {len(regions)} régions dans le COG (101 et 18 attendus)")
        return 1

    contours = json.load(gzip.open(RAW / "departements-100m.geojson.gz", "rt", encoding="utf-8"))
    units = {f["properties"]["code"]: to_polygons(f["geometry"]) for f in contours["features"]
             if f["properties"]["code"] in {d["DEP"] for d in departments}}
    missing = {d["DEP"] for d in departments} - units.keys()
    if missing:
        print(f"ERREUR : départements sans contour : {sorted(missing)}")
        return 1
    mairies = {f["properties"]["commune"]: f["geometry"]["coordinates"]
               for f in json.load(gzip.open(RAW / "mairies.geojson.gz", "rt", encoding="utf-8"))["features"]}

    # Cadre de la métropole : Lambert-93, y vers le bas, en unités de 100 m.
    def metro_to_map(lon, lat):
        x, y = lambert93(lon, lat)
        return x / GRID_KM, -y / GRID_KM

    metro_codes = [d["DEP"] for d in departments if d["DEP"] not in OVERSEAS_CODES]
    frames = {code: {"to_map": metro_to_map, "tolerance": 2.5, "scale": 1.0, "frame": "metro"} for code in metro_codes}
    pts = [metro_to_map(lon / 1e7, lat / 1e7) for c in metro_codes for p in units[c] for r in p for lon, lat in r]
    margin = 250  # 25 km de mer ou de pays voisins autour de la métropole
    mx0, my0 = min(x for x, _ in pts) - margin, min(y for _, y in pts) - margin
    mx1, my1 = max(x for x, _ in pts) + margin, max(y for _, y in pts) + margin
    frame_list = [{"id": "metro", "bounds": [round(mx0), round(my0), round(mx1), round(my1)]}]

    # Cartouches de l'outre-mer : une rangée de carrés sous la métropole, chacun à sa propre échelle.
    gap = (mx1 - mx0) * 0.02
    box = ((mx1 - mx0) - gap * (len(OVERSEAS_CODES) - 1)) / len(OVERSEAS_CODES)
    for index, code in enumerate(OVERSEAS_CODES):
        lonlat = [(lon / 1e7, lat / 1e7) for p in units[code] for r in p for lon, lat in r]
        lon_c = (min(p[0] for p in lonlat) + max(p[0] for p in lonlat)) / 2
        lat_c = (min(p[1] for p in lonlat) + max(p[1] for p in lonlat)) / 2
        local = local_projection(lon_c, lat_c)
        km = [local(lon, lat) for lon, lat in lonlat]
        extent = max(max(x for x, _ in km) - min(x for x, _ in km), max(y for _, y in km) - min(y for _, y in km))
        scale = (box * 0.86 * GRID_KM) / extent  # rapport à l'échelle de la métropole
        bx0, by0 = mx0 + index * (box + gap), my1 + gap
        cx, cy = bx0 + box / 2, by0 + box / 2

        def to_map(lon, lat, local=local, scale=scale, cx=cx, cy=cy):
            x, y = local(lon, lat)
            return cx + x * scale / GRID_KM, cy - y * scale / GRID_KM

        frames[code] = {"to_map": to_map, "tolerance": 2.5, "scale": scale, "frame": code}
        frame_list.append({"id": code, "bounds": [round(bx0), round(by0), round(bx0 + box), round(by0 + box)], "scale": round(scale, 3)})

    simplified = simplify_units(units, frames)
    empty = [c for c, p in simplified.items() if not p]
    if empty:
        print(f"ERREUR : départements vides après simplification : {empty}")
        return 1

    # Chefs-lieux : mairie de la commune chef-lieu, ramenée à l'intérieur du contour simplifié si besoin.
    labels = {code: label_point(polygons) for code, polygons in simplified.items()}
    capitals = {}
    for d in departments:
        code = d["DEP"]
        commune = d["CHEFLIEU"]
        lon, lat = mairies[commune]
        x, y = frames[code]["to_map"](lon, lat)
        (px, py), moved = place_inside(simplified[code], x, y, margin=3)
        if moved > 0:
            print(f"  chef-lieu {communes[commune]['LIBELLE']} ({code}) rentré de {moved * GRID_KM / frames[code]['scale']:.1f} km dans les terres")
        capitals[code] = (round(px), round(py))

    # --- Catalogue ---
    department_population, region_population = read_populations()
    areas = read_areas()
    region_of = {d["DEP"]: REGION_IDS[d["REG"]] for d in departments}
    region_entities = []
    for r in sorted(regions, key=lambda r: (r["REG"] in ("01", "02", "03", "04", "06"), r["LIBELLE"])):
        rid = REGION_IDS[r["REG"]]
        capital_dep = communes[r["CHEFLIEU"]]["DEP"]
        entity = {"id": rid, "type": "region", "name": r["LIBELLE"], "code": r["REG"], "capitalId": f"CL-{capital_dep}"}
        if r["REG"] in ("01", "02", "03", "04", "06"):
            entity["overseas"] = True
        if EXTRA_ALIASES.get(rid):
            entity["aliases"] = EXTRA_ALIASES[rid]
        if r["REG"] in region_population:
            entity["population"] = region_population[r["REG"]]
            entity["populationYear"] = POPULATION_YEAR
        entity["sourceId"] = INSEE_SOURCE
        region_entities.append(entity)

    department_entities, capital_entities = [], []
    for d in sorted(departments, key=lambda d: department_order(d["DEP"])):
        code = d["DEP"]
        did = f"DEP-{code}"
        entity = {"id": did, "type": "department", "name": d["LIBELLE"], "code": code, "capitalId": f"CL-{code}", "parentId": region_of[code]}
        aliases = saint_aliases(d["LIBELLE"]) + EXTRA_ALIASES.get(did, [])
        if aliases:
            entity["aliases"] = aliases
        if code in OVERSEAS_CODES:
            entity["overseas"] = True
        if did in NOTES:
            entity["note"], entity["noteSource"] = NOTES[did]
        # Chiffres officiels de l'INSEE ; Mayotte n'y figure pas : rien plutôt qu'une autre source.
        if code in areas:
            entity["areaKm2"] = round(areas[code])
        if code in department_population:
            entity["population"] = department_population[code]
            entity["populationYear"] = POPULATION_YEAR
        entity["sourceId"] = INSEE_SOURCE
        department_entities.append(entity)

        commune = communes[d["CHEFLIEU"]]
        cap = {"id": f"CL-{code}", "type": "prefecture", "name": commune["LIBELLE"], "parentId": did, "inseeCode": d["CHEFLIEU"]}
        cap_aliases = saint_aliases(commune["LIBELLE"]) + EXTRA_ALIASES.get(f"CL-{code}", [])
        if cap_aliases:
            cap["aliases"] = cap_aliases
        cap["sourceId"] = INSEE_SOURCE
        capital_entities.append(cap)

    OUT.mkdir(parents=True, exist_ok=True)
    write_catalog("regions.json", region_entities, SOURCES[:1])
    write_catalog("departements.json", department_entities, SOURCES)
    write_catalog("chefs-lieux.json", capital_entities, SOURCES)

    # Vérification : aucun nom ni alias ne doit se confondre avec un autre de la même famille.
    for family in (region_entities, department_entities, capital_entities):
        seen = {}
        for e in family:
            for n in [e["name"]] + e.get("aliases", []):
                k = normalized(n)
                if seen.get(k, e["id"]) != e["id"]:
                    print(f"ERREUR : « {n} » ({e['id']}) se confond avec {seen[k]}")
                    return 1
                seen[k] = e["id"]

    # --- Géométrie ---
    context = context_polygons(metro_to_map, (mx0, my0, mx1, my1))
    all_x = [b for f in frame_list for b in (f["bounds"][0], f["bounds"][2])]
    all_y = [b for f in frame_list for b in (f["bounds"][1], f["bounds"][3])]
    header = {
        "schemaVersion": 1,
        "sourceId": IGN_SOURCE,
        "projection": {
            "metro": "Lambert-93 (EPSG:2154), en unités de 100 m, y vers le bas",
            "overseas": "équirectangulaire locale par territoire, mise à l'échelle de son cartouche",
        },
        "unitKm": GRID_KM,
        "yAxis": "down",
        "bounds": [min(all_x), min(all_y), max(all_x), max(all_y)],
        "frames": frame_list,
    }
    lines = []
    for code in sorted(simplified, key=department_order):
        polygons = [[[c for point in ring for c in point] for ring in polygon] for polygon in simplified[code]]
        lines.append(json.dumps({"id": f"DEP-{code}", "frame": frames[code]["frame"], "label": labels[code], "polygons": polygons}, separators=(",", ":")))
    for code in sorted(capitals, key=department_order):
        lines.append(json.dumps({"id": f"CL-{code}", "kind": "point", "frame": frames[code]["frame"], "point": list(capitals[code])}, separators=(",", ":")))
    context_lines = [json.dumps({"polygons": [[[c for point in ring for c in point] for ring in poly]]}, separators=(",", ":")) for poly in context]
    text = (json.dumps(header, ensure_ascii=False)[:-1] + ',\n"features":[\n' + ",\n".join(lines)
            + '\n],\n"context":[\n' + ",\n".join(context_lines) + "\n]}\n")
    (OUT / "map.geometry.json").write_text(text, encoding="utf-8", newline="\n")

    before = sum(len(r) for ps in units.values() for p in ps for r in p)
    after = sum(len(r) for ps in simplified.values() for p in ps for r in p)
    size = (OUT / "map.geometry.json").stat().st_size // 1024
    print(f"{len(simplified)} départements, sommets {before} -> {after}, {len(context)} polygones de fond, map.geometry.json {size} Ko")
    for f in frame_list[1:]:
        print(f"  cartouche {f['id']} : échelle ×{f['scale']} par rapport à la métropole")
    return 0


if __name__ == "__main__":
    sys.exit(main())
