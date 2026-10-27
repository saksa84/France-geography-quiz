"""Produit les données des cours d'eau (version 2) à partir de BD TOPAGE 2025.

Usage (depuis la racine du projet, après tools/download_sources.py et tools/build_geo.py) :
    python -X utf8 tools/build_rivers.py

Entrées :
- tools/raw/topage2025-cours-eau.geojson : les cours d'eau de la table RIVERS, téléchargés par
  download_sources.py depuis le service WFS du Sandre (BD TOPAGE 2025, Licence Ouverte 2.0) ;
- app/src/main/assets/geo/map.geometry.json : contours des départements déjà projetés (build_geo.py),
  pour savoir quels départements chaque cours d'eau traverse.

Sorties (app/src/main/assets/geo/) :
- cours-eau.json : les cours d'eau (nom, alias avec l'article, fleuve ou affluent, départements traversés) ;
- cours-eau.geometry.json : leurs tracés, projetés comme la métropole (Lambert-93, unités de 100 m, y vers le bas).

Seule la bibliothèque standard de Python est utilisée.
"""

import json
import math
import sys
import urllib.parse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_geo import GRID_KM, OUT, RAW, douglas_peucker, lambert93, normalized, point_in_rings  # noqa: E402

SOURCE = {
    "id": "sandre-bd-topage-2025",
    "name": "Sandre, BD TOPAGE® 2025 (OFB, IGN), couche CoursEau_FXX",
    "license": "Licence Ouverte Etalab 2.0",
    "url": "https://www.sandre.eaufrance.fr/atlas/srv/fre/catalog.search#/metadata/7fa4c224-fe38-4e2c-846d-dcc2fa7ef73e",
    "version": "2025",
}
RAW_FILE = "topage2025-cours-eau.geojson"
WFS = "https://services.sandre.eaufrance.fr/geo/topage2025"
LAYER = "sa:CoursEau_FXX_Topage2025"

# Cours d'eau retenus : identifiant stable, nom, codes hydrographiques BD TOPAGE (CdOH), fleuve dans
# lequel il se jette (None : fleuve qui se jette dans la mer), et l'article Wikipédia (révision) où ce
# rattachement a été vérifié le 27 octobre 2026. La Somme et l'Escaut sont découpés en deux objets
# dans BD TOPAGE (cours amont et partie canalisée). La Dordogne est un fleuve : elle forme l'estuaire
# de la Gironde avec la Garonne.
RIVERS = [
    # Fleuves
    ("RV-LOIRE", "Loire", ["04C0000002000808543"], None, "Loire (fleuve)", 240649704),
    ("RV-SEINE", "Seine", ["03C0000002000844903"], None, "Seine", 240626933),
    ("RV-RHONE", "Rhône", ["06C0000002000804457"], None, "Rhône", 240627923),
    ("RV-GARONNE", "Garonne", ["05C0000002000894629"], None, "Garonne", 239607364),
    ("RV-DORDOGNE", "Dordogne", ["05C0000002000831663"], None, "Dordogne (fleuve)", 240153910),
    ("RV-RHIN", "Rhin", ["02C0000002000786588"], None, "Rhin", 239996481),
    ("RV-MEUSE", "Meuse", ["02C0000002000797618"], None, "Meuse (fleuve)", 229213129),
    ("RV-ESCAUT", "Escaut", ["01C0000002000822354", "01C0000002000815351"], None, "Escaut", 240211503),
    ("RV-SOMME", "Somme", ["01C0000002000818638", "01C0000002000824194"], None, "Somme (fleuve)", 239809398),
    ("RV-ORNE", "Orne", ["03C0000002000845629"], None, "Orne (fleuve)", 235610395),
    ("RV-VILAINE", "Vilaine", ["04C0000002000866869"], None, "Vilaine (fleuve)", 240480303),
    ("RV-CHARENTE", "Charente", ["05C0000002000863964"], None, "Charente (fleuve)", 236845181),
    ("RV-ADOUR", "Adour", ["05C0000002000898951"], None, "Adour", 240654562),
    ("RV-AUDE", "Aude", ["06C0000002000839758"], None, "Aude (fleuve)", 240699672),
    ("RV-HERAULT", "Hérault", ["06C0000002000818061"], None, "Hérault (fleuve)", 240657169),
    ("RV-VAR", "Var", ["06C0000002000789272"], None, "Var (fleuve)", 238517753),
    # Bassin de la Loire
    ("RV-ALLIER", "Allier", ["04C0000002000820586"], "RV-LOIRE", "Allier (rivière)", 239247191),
    ("RV-CHER", "Cher", ["04C0000002000831254"], "RV-LOIRE", "Cher (rivière)", 232201581),
    ("RV-INDRE", "Indre", ["04C0000002000857273"], "RV-LOIRE", "Indre (rivière)", 235153535),
    ("RV-VIENNE", "Vienne", ["04C0000002000863855"], "RV-LOIRE", "Vienne (affluent de la Loire)", 234781206),
    ("RV-CREUSE", "Creuse", ["04C0000002000857620"], "RV-VIENNE", "Creuse (affluent de la Vienne)", 239430650),
    ("RV-MAINE", "Maine", ["04C0000002000855547"], "RV-LOIRE", "Maine (affluent de la Loire)", 238887656),
    ("RV-MAYENNE", "Mayenne", ["04C0000002000851861"], "RV-MAINE", "Mayenne (rivière)", 240706793),
    ("RV-SARTHE", "Sarthe", ["04C0000002000848792"], "RV-MAINE", "Sarthe (rivière)", 240062134),
    ("RV-LOIR", "Loir", ["04C0000002000849052"], "RV-SARTHE", "Loir (rivière)", 233770407),
    # Bassin de la Seine
    ("RV-MARNE", "Marne", ["03C0000002000815787"], "RV-SEINE", "Marne (rivière)", 240055284),
    ("RV-OISE", "Oise", ["03C0000002000807131"], "RV-SEINE", "Oise (rivière)", 240552886),
    ("RV-AISNE", "Aisne", ["03C0000002000807320"], "RV-OISE", "Aisne (affluent de l'Oise)", 238788869),
    ("RV-YONNE", "Yonne", ["03C0000002000827041"], "RV-SEINE", "Yonne (rivière)", 237989041),
    ("RV-AUBE", "Aube", ["03C0000002000813408"], "RV-SEINE", "Aube (rivière)", 236501057),
    ("RV-EURE", "Eure", ["03C0000002000844626"], "RV-SEINE", "Eure (rivière)", 234945002),
    # Bassin du Rhône
    ("RV-SAONE", "Saône", ["06C0000002000794279"], "RV-RHONE", "Saône", 240287319),
    ("RV-DOUBS", "Doubs", ["06C0000002000794423"], "RV-SAONE", "Doubs (rivière)", 239784035),
    ("RV-AIN", "Ain", ["06C0000002000802959"], "RV-RHONE", "Ain (rivière)", 228501591),
    ("RV-ISERE", "Isère", ["06C0000002000788975"], "RV-RHONE", "Isère (rivière)", 239802851),
    ("RV-DROME", "Drôme", ["06C0000002000803141"], "RV-RHONE", "Drôme (rivière)", 234886132),
    ("RV-ARDECHE", "Ardèche", ["06C0000002000809358"], "RV-RHONE", "Ardèche (rivière)", 238848678),
    ("RV-DURANCE", "Durance", ["06C0000002000789104"], "RV-RHONE", "Durance", 240076657),
    # Bassin de la Garonne et de la Dordogne
    ("RV-ARIEGE", "Ariège", ["05C0000002000885993"], "RV-GARONNE", "Ariège (rivière)", 240564490),
    ("RV-TARN", "Tarn", ["05C0000002000815043"], "RV-GARONNE", "Tarn (rivière)", 240190358),
    ("RV-AVEYRON", "Aveyron", ["05C0000002000828961"], "RV-TARN", "Aveyron (rivière)", 230116183),
    ("RV-LOT", "Lot", ["05C0000002000835176"], "RV-GARONNE", "Lot (rivière)", 238644355),
    ("RV-GERS", "Gers", ["05C0000002000889096"], "RV-GARONNE", "Gers (rivière)", 228210386),
    ("RV-VEZERE", "Vézère", ["05C0000002000841582"], "RV-DORDOGNE", "Vézère", 238655358),
    # Bassin du Rhin
    ("RV-MOSELLE", "Moselle", ["02C0000002000793398"], "RV-RHIN", "Moselle (rivière)", 239541381),
    ("RV-MEURTHE", "Meurthe", ["02C0000002000793627"], "RV-MOSELLE", "Meurthe (rivière)", 240460038),
    ("RV-ILL", "Ill", ["02C0000002000786646"], "RV-RHIN", "Ill (France)", 240768095),
]

TOLERANCE = 2.5  # 250 m, comme les frontières des départements
MIN_PART = 10  # tronçons isolés de moins de 1 km après simplification : écartés
SAMPLE = 5  # pas d'échantillonnage (500 m) pour mesurer la longueur dans chaque département
MIN_IN_DEPARTMENT = 50  # un département est « traversé » à partir de 5 km de cours


def topage_url():
    """Requête WFS de tous les cours d'eau de RIVERS, triés pour que la réponse soit toujours identique."""
    codes = [code for river in RIVERS for code in river[2]]
    condition = "".join(f"<PropertyIsEqualTo><PropertyName>CdOH</PropertyName><Literal>{c}</Literal></PropertyIsEqualTo>" for c in codes)
    return WFS + "?" + urllib.parse.urlencode({
        "SERVICE": "WFS", "VERSION": "2.0.0", "REQUEST": "GetFeature", "TYPENAMES": LAYER,
        "OUTPUTFORMAT": "application/json; subtype=geojson", "SORTBY": "CdOH",
        "FILTER": f"<Filter><Or>{condition}</Or></Filter>",
    })


def metro_to_map(lon, lat):
    x, y = lambert93(lon, lat)
    return x / GRID_KM, -y / GRID_KM


def length(points):
    return sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(points, points[1:]))


def point_along(points, distance):
    """Point situé à `distance` du début du tracé."""
    for a, b in zip(points, points[1:]):
        step = math.hypot(b[0] - a[0], b[1] - a[1])
        if step >= distance and step > 0:
            t = distance / step
            return a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])
        distance -= step
    return points[-1]


def samples(points):
    """Points régulièrement espacés de SAMPLE le long du tracé."""
    total, d = length(points), SAMPLE / 2
    while d < total:
        yield point_along(points, d)
        d += SAMPLE


def main() -> int:
    collection = json.loads((RAW / RAW_FILE).read_text(encoding="utf-8"))
    by_code = {}
    for feature in collection["features"]:
        g = feature["geometry"]
        parts = g["coordinates"] if g["type"] == "MultiLineString" else [g["coordinates"]]
        by_code[feature["properties"]["CdOH"]] = (feature["properties"]["TopoOH"], parts)
    missing = [c for r in RIVERS for c in r[2] if c not in by_code]
    if missing:
        print(f"ERREUR : codes absents du fichier téléchargé : {missing}")
        return 1

    geometry = json.loads((OUT / "map.geometry.json").read_text(encoding="utf-8"))
    metro = next(f for f in geometry["frames"] if f["id"] == "metro")["bounds"]
    departments = []
    for f in geometry["features"]:
        if f.get("kind", "polygon") == "polygon" and f["frame"] == "metro":
            rings = [list(zip(r[0::2], r[1::2])) for polygon in f["polygons"] for r in polygon]
            xs = [x for r in rings for x, _ in r]
            ys = [y for r in rings for _, y in r]
            departments.append((f["id"], rings, (min(xs), min(ys), max(xs), max(ys))))

    def department_at(x, y):
        for dep_id, rings, (x0, y0, x1, y1) in departments:
            if x0 <= x <= x1 and y0 <= y <= y1 and point_in_rings(rings, x, y):
                return dep_id
        return None

    ids = {r[0] for r in RIVERS}
    entities, features = [], []
    for river_id, name, codes, parent, _title, _revision in RIVERS:
        if parent is not None and parent not in ids:
            print(f"ERREUR : {river_id} se jette dans {parent}, absent de la liste")
            return 1
        projected = [[metro_to_map(lon, lat) for lon, lat in part] for code in codes for part in by_code[code][1]]
        # Alias : le nom avec son article, tel que l'écrit BD TOPAGE (« la Loire », « l'Isère »), sans
        # « Fleuve » ni « Rivière ». Il faut qu'au moins un objet porte ce nom (la Somme amont s'appelle
        # « la Somme Rivière », sa partie aval « la Somme Canalisée » : pas d'alias tiré de BD TOPAGE).
        names = [by_code[c][0] for c in codes]
        cleaned = [n.removeprefix("Fleuve ").removeprefix("Rivière ") for n in names]
        if not any(normalized(n).removesuffix(normalized(name)) in ("", "la", "le", "l", "les") for n in cleaned):
            if river_id not in ("RV-SOMME",):
                print(f"ERREUR : nom BD TOPAGE inattendu pour {river_id} : {names}")
                return 1
        topo = next((n for n in cleaned if normalized(n).removesuffix(normalized(name)) in ("la", "le", "l", "les")), name)

        lines = []
        for part in projected:
            simple = douglas_peucker(part, TOLERANCE)
            if len(simple) >= 2 and length(simple) >= MIN_PART:
                lines.append([round(c) for p in simple for c in p])
        if not lines:
            print(f"ERREUR : {river_id} vide après simplification")
            return 1
        outside = [(x, y) for line in lines for x, y in zip(line[0::2], line[1::2])
                   if not (metro[0] <= x <= metro[2] and metro[1] <= y <= metro[3])]
        if outside:
            print(f"ERREUR : {river_id} sort du cadre de la métropole ({len(outside)} points)")
            return 1

        # Étiquette : milieu du plus long tronçon.
        longest = max(projected, key=length)
        lx, ly = point_along(longest, length(longest) / 2)

        # Départements traversés : longueur de cours mesurée dans chacun (tracé complet, avant simplification).
        inside = {}
        for part in projected:
            for x, y in samples(part):
                dep = department_at(x, y)
                if dep:
                    inside[dep] = inside.get(dep, 0) + SAMPLE
        crossed = sorted((d for d, n in inside.items() if n >= MIN_IN_DEPARTMENT), key=lambda d: [g["id"] for g in geometry["features"]].index(d))

        entity = {"id": river_id, "type": "river", "name": name, "aliases": [topo] if topo != name else []}
        if parent:
            entity["parentId"] = parent
        entity["departmentIds"] = crossed
        entity["sandreCodes"] = codes
        entity["sourceId"] = SOURCE["id"]
        entities.append(entity)
        features.append({"id": river_id, "kind": "line", "frame": "metro", "label": [round(lx), round(ly)], "lines": lines})
        km = sum(map(length, projected)) * GRID_KM
        print(f"  {name:<9} {km:6.0f} km, {sum(len(l) // 2 for l in lines):4d} points, {' '.join(d[4:] for d in crossed)}")

    # Aucun nom ni alias ne doit se confondre avec un autre cours d'eau (mode Saisir).
    seen = {}
    for e in entities:
        for n in [e["name"]] + e["aliases"]:
            k = normalized(n)
            if seen.get(k, e["id"]) != e["id"]:
                print(f"ERREUR : « {n} » ({e['id']}) se confond avec {seen[k]}")
                return 1
            seen[k] = e["id"]

    catalog = {"schemaVersion": 1, "sources": [SOURCE], "entities": entities}
    lines_out = [json.dumps(e, ensure_ascii=False, separators=(", ", ": ")) for e in entities]
    text = (json.dumps({k: v for k, v in catalog.items() if k != "entities"}, ensure_ascii=False)[:-1]
            + ',\n"entities": [\n' + ",\n".join(lines_out) + "\n]}\n")
    (OUT / "cours-eau.json").write_text(text, encoding="utf-8", newline="\n")

    header = {
        "schemaVersion": 1,
        "sourceId": SOURCE["id"],
        "projection": {"metro": "Lambert-93 (EPSG:2154), en unités de 100 m, y vers le bas"},
        "unitKm": GRID_KM,
        "yAxis": "down",
        "bounds": geometry["bounds"],
    }
    text = (json.dumps(header, ensure_ascii=False)[:-1] + ',\n"features":[\n'
            + ",\n".join(json.dumps(f, separators=(",", ":")) for f in features) + "\n]}\n")
    (OUT / "cours-eau.geometry.json").write_text(text, encoding="utf-8", newline="\n")
    size = (OUT / "cours-eau.geometry.json").stat().st_size // 1024
    print(f"{len(entities)} cours d'eau, cours-eau.geometry.json {size} Ko")
    return 0


if __name__ == "__main__":
    sys.exit(main())
