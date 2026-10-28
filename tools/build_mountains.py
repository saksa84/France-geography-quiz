"""Produit les données des montagnes (version 2) : massifs et sommets.

Usage (depuis la racine du projet, après tools/download_sources.py et tools/build_geo.py) :
    python -X utf8 tools/build_mountains.py

Entrées (tools/raw/, voir docs/data-sources.md) :
- diffusion-zonages-massifs-cog2021.xls : ANCT, communes classées dans un massif (décret du 16 janvier 2004
  modifié le 8 septembre 2016), en COG 2021 ;
- communes-100m.geojson.gz : contours des communes 2026 (IGN ADMIN EXPRESS, généralisés par Etalab) ;
- v_commune_2026.csv, v_mvt_commune_2026.csv : COG 2026 de l'INSEE, pour passer des communes 2021 aux communes 2026 ;
- wikidata/*.json : un sommet par fichier, à une révision fixée (coordonnées, altitude).

Sorties (app/src/main/assets/geo/) :
- montagnes.json : massifs (départements concernés) et sommets (altitude, massif, département) ;
- montagnes.geometry.json : contours des massifs (union des communes) et points des sommets.

Seule la bibliothèque standard de Python est utilisée (plus tools/xls_reader.py pour le fichier .xls).
"""

import csv
import gzip
import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_geo import (OUT, RAW, build_frames, department_units, douglas_peucker, key, label_point,  # noqa: E402
                       normalized, point_in_rings, read_csv, ring_area)
from xls_reader import read_sheets  # noqa: E402

MASSIF_FILE = "diffusion-zonages-massifs-cog2021.xls"
MASSIF_SHEET = "Communes de massif (COG 2021)"
WIKIDATA_DIR = RAW / "wikidata"

SOURCES = [
    {"id": "anct-massifs-2016", "name": "ANCT, Observatoire des territoires : communes classées en zones de massif (décret du 16 janvier 2004 modifié le 8 septembre 2016), COG 2021", "license": "Licence Ouverte Etalab", "url": "https://www.data.gouv.fr/datasets/les-perimetres-de-massifs", "version": "2021"},
    {"id": "wikidata", "name": "Wikidata : coordonnées et altitude des sommets (révision indiquée pour chacun)", "license": "CC0 1.0", "url": "https://www.wikidata.org"},
]

# Massifs : identifiant stable, nom affiché, libellé dans le fichier de l'ANCT.
MASSIFS = [
    ("MT-ALPES", "Alpes", "Alpes"),
    ("MT-PYRENEES", "Pyrénées", "Pyrénées"),
    ("MT-MASSIF-CENTRAL", "Massif central", "Massif Central"),
    ("MT-JURA", "Jura", "Jura"),
    ("MT-VOSGES", "Vosges", "Vosges"),
    ("MT-CORSE", "Corse", "Corse"),
    ("MT-GUADELOUPE", "Guadeloupe", "Guadeloupe"),
    ("MT-MARTINIQUE", "Martinique", "Martinique"),
    ("MT-REUNION", "Hauts de la Réunion", "Hauts de la Réunion"),
]

# Sommets : identifiant, nom, massif, élément Wikidata et révision consultée le 28 octobre 2026.
PEAKS = [
    ("PK-MONTBLANC", "Mont Blanc", "MT-ALPES", "Q583", 2557648539),
    ("PK-ECRINS", "Barre des Écrins", "MT-ALPES", "Q30480", 2522831192),
    ("PK-MEIJE", "La Meije", "MT-ALPES", "Q1605356", 2491066154),
    ("PK-GRANDECASSE", "Grande Casse", "MT-ALPES", "Q1542960", 2491061958),
    ("PK-AIGUILLE", "Mont Aiguille", "MT-ALPES", "Q1337859", 2490987051),
    ("PK-VENTOUX", "Mont Ventoux", "MT-ALPES", "Q209064", 2529359607),
    ("PK-VIGNEMALE", "Vignemale", "MT-PYRENEES", "Q1348903", 2486070096),
    ("PK-MIDIBIGORRE", "Pic du Midi de Bigorre", "MT-PYRENEES", "Q1139060", 2532621197),
    ("PK-CANIGOU", "Canigou", "MT-PYRENEES", "Q6650", 2552213170),
    ("PK-SANCY", "Puy de Sancy", "MT-MASSIF-CENTRAL", "Q778597", 2559308375),
    ("PK-PUYDEDOME", "Puy de Dôme", "MT-MASSIF-CENTRAL", "Q607372", 2542886550),
    ("PK-PLOMBCANTAL", "Plomb du Cantal", "MT-MASSIF-CENTRAL", "Q991088", 2513180040),
    ("PK-PUYMARY", "Puy Mary", "MT-MASSIF-CENTRAL", "Q2119428", 2491099656),
    ("PK-MEZENC", "Mont Mézenc", "MT-MASSIF-CENTRAL", "Q1267547", 2486070055),
    ("PK-GERBIER", "Mont Gerbier de Jonc", "MT-MASSIF-CENTRAL", "Q626222", 2500593331),
    ("PK-LOZERE", "Mont Lozère", "MT-MASSIF-CENTRAL", "Q605382", 2549165378),
    ("PK-AIGOUAL", "Mont Aigoual", "MT-MASSIF-CENTRAL", "Q1836325", 2491082355),
    ("PK-CRETNEIGE", "Crêt de la Neige", "MT-JURA", "Q1142554", 2558943226),
    ("PK-GRANDCOLOMBIER", "Grand Colombier", "MT-JURA", "Q748241", 2560250288),
    ("PK-GRANDBALLON", "Grand Ballon", "MT-VOSGES", "Q1397720", 2528040895),
    ("PK-HOHNECK", "Hohneck", "MT-VOSGES", "Q1624140", 2486070289),
    ("PK-BALLONALSACE", "Ballon d'Alsace", "MT-VOSGES", "Q1334057", 2519225099),
    ("PK-DONON", "Donon", "MT-VOSGES", "Q1242068", 2486070052),
    ("PK-CINTO", "Monte Cinto", "MT-CORSE", "Q1415428", 2502221392),
    ("PK-ROTONDO", "Monte Rotondo", "MT-CORSE", "Q641239", 2486069830),
    ("PK-ORO", "Monte d'Oro", "MT-CORSE", "Q2572089", 2491143632),
    ("PK-SOUFRIERE", "La Soufrière", "MT-GUADELOUPE", "Q1149310", 2552213596),
    ("PK-PELEE", "Montagne Pelée", "MT-MARTINIQUE", "Q76309", 2549196957),
    ("PK-PITONNEIGES", "Piton des Neiges", "MT-REUNION", "Q1545452", 2542791065),
    ("PK-FOURNAISE", "Piton de la Fournaise", "MT-REUNION", "Q1049644", 2548667738),
]

# Autres graphies acceptées dans le mode Saisir : titre de l'article Wikipédia, nom sans son article.
ALIASES = {
    "PK-CANIGOU": ["Pic du Canigou"],
    "PK-MEIJE": ["Meije"],
    "PK-SOUFRIERE": ["Soufrière"],
    "MT-REUNION": ["Réunion"],
}

MIN_RING = 100  # anneaux de moins de 1 km² (en unités de carte, 100 m × 100 m) : écartés


def wikidata_url(item, revision):
    return f"https://www.wikidata.org/wiki/Special:EntityData/{item}.json?revision={revision}"


def wikidata_files():
    """Fichiers à télécharger (download_sources.py) : un par sommet, à sa révision. Une révision ne change jamais."""
    return {f"wikidata/{item}-{revision}.json": wikidata_url(item, revision) for _, _, _, item, revision in PEAKS if item}


def massif_of_communes():
    """Massif de chaque commune 2026, d'après le fichier de l'ANCT (communes 2021)."""
    rows = read_sheets(RAW / MASSIF_FILE)[MASSIF_SHEET]
    header = next(i for i, r in enumerate(rows) if r and r[0] == "CODGEO")
    by_2021 = {r[0]: r[2] for r in rows[header + 1:] if r and r[0]}
    labels = {label: mid for mid, _, label in MASSIFS}

    def massif(label):
        # « Jura (partiellement) » : la commune n'est qu'en partie dans le massif ; on la compte dedans.
        return labels.get(label.removesuffix(" (partiellement)")) if label else None

    # Communes 2021 dont la commune 2026 est issue : elle-même, ses communes déléguées ou associées,
    # et les communes d'avant une fusion ou un rétablissement (fichier des mouvements de l'INSEE).
    origins = defaultdict(set)
    for r in read_csv("v_commune_2026.csv"):
        if r["TYPECOM"] == "COM":
            origins[r["COM"]].add(r["COM"])
        elif r["TYPECOM"] in ("COMD", "COMA") and r["COMPARENT"]:
            origins[r["COMPARENT"]].add(r["COM"])
    communes_2026 = set(origins)
    for r in read_csv("v_mvt_commune_2026.csv"):
        if r["DATE_EFF"] >= "2021-01-01" and r["TYPECOM_AP"] == "COM" and r["COM_AP"] in communes_2026:
            origins[r["COM_AP"]].add(r["COM_AV"])

    result, unknown = {}, []
    for com, codes in origins.items():
        found = {massif(by_2021[c]) for c in codes if c in by_2021}
        if not found:
            unknown.append(com)
            continue
        inside = found - {None}
        if len(inside) > 1:
            raise ValueError(f"commune {com} dans plusieurs massifs : {inside}")
        if inside:
            result[com] = inside.pop()
    return result, unknown


def union_rings(polygons):
    """
    Union de polygones voisins (les communes d'un massif) : les contours IGN/Etalab sont topologiques,
    deux communes voisines partagent exactement les mêmes sommets. Une arête commune à deux communes
    est intérieure ; celles qui ne servent qu'une fois forment le contour, qu'on raboute en anneaux.
    """
    count = defaultdict(int)
    for polygon in polygons:
        for ring in polygon:
            for a, b in zip(ring, ring[1:] + ring[:1]):
                if a != b:
                    count[(a, b) if a < b else (b, a)] += 1
    neighbours = defaultdict(list)
    for (a, b), n in count.items():
        if n % 2:
            neighbours[a].append(b)
            neighbours[b].append(a)
    used = set()
    rings = []
    for start in list(neighbours):
        for nxt in neighbours[start]:
            if (start, nxt) in used:
                continue
            ring, prev, cur = [start], start, nxt
            used.add((start, nxt)); used.add((nxt, start))
            while cur != start:
                ring.append(cur)
                options = [n for n in neighbours[cur] if (cur, n) not in used]
                if not options:
                    break
                prev, cur = cur, options[0]
                used.add((prev, cur)); used.add((cur, prev))
            if len(ring) >= 3:
                rings.append(ring)
    return rings


def wikidata_peak(item, revision):
    entity = json.loads((WIKIDATA_DIR / f"{item}-{revision}.json").read_text(encoding="utf-8"))["entities"][item]
    claims = entity["claims"]
    coord = claims["P625"][0]["mainsnak"]["datavalue"]["value"]
    elevations = [c for c in claims.get("P2044", []) if "datavalue" in c["mainsnak"] and c["rank"] != "deprecated"
                  and c["mainsnak"]["datavalue"]["value"]["unit"].endswith("/Q11573")]  # en mètres

    def when(c):
        dates = c.get("qualifiers", {}).get("P585", [])
        return dates[0]["datavalue"]["value"]["time"] if dates else ""

    # Valeur « préférée » s'il y en a une, sinon la mesure la plus récente (date P585).
    best = max(elevations, key=lambda c: (c["rank"] == "preferred", when(c)))
    return coord["longitude"], coord["latitude"], float(best["mainsnak"]["datavalue"]["value"]["amount"])


def main() -> int:
    departments = read_csv("v_departement_2026.csv")
    units = department_units({d["DEP"] for d in departments})
    frames, _, _ = build_frames(units, departments)

    massif_of, unknown = massif_of_communes()
    if unknown:
        print(f"ERREUR : communes 2026 introuvables dans le fichier des massifs : {unknown}")
        return 1

    contours = json.load(gzip.open(RAW / "communes-100m.geojson.gz", "rt", encoding="utf-8"))
    members = defaultdict(list)
    departments_of = defaultdict(set)
    for f in contours["features"]:
        com = f["properties"]["code"]
        mid = massif_of.get(com)
        if mid is None:
            continue
        g = f["geometry"]
        polygons = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        members[mid].append([[key(lon, lat) for lon, lat in ring[:-1]] for ring in polygon] for polygon in polygons)
        departments_of[mid].add(f["properties"]["departement"])

    geometry = json.loads((OUT / "map.geometry.json").read_text(encoding="utf-8"))
    order = [f["id"] for f in geometry["features"]]
    shapes = {f["id"]: [list(zip(r[0::2], r[1::2])) for p in f["polygons"] for r in p]
              for f in geometry["features"] if f.get("kind", "polygon") == "polygon"}

    entities, features, massif_rings = [], [], {}
    for mid, name, _ in MASSIFS:
        communes = [p for commune in members[mid] for p in commune]
        codes = sorted(departments_of[mid])
        frame = frames[codes[0]]
        rings = []
        for ring in union_rings(communes):
            projected = [frame["to_map"](lon / 1e7, lat / 1e7) for lon, lat in ring]
            simple = douglas_peucker(projected + projected[:1], frame["tolerance"])[:-1]
            if len(simple) >= 3 and ring_area(simple) >= MIN_RING:
                rings.append(simple)
        if not rings:
            print(f"ERREUR : massif {mid} vide")
            return 1
        massif_rings[mid] = rings
        label = label_point([[r] for r in rings])
        entities.append({"id": mid, "type": "massif", "name": name, "aliases": ALIASES.get(mid, []), "overseas": len(codes[0]) == 3, "departmentIds": sorted((f"DEP-{c}" for c in codes), key=order.index),
                         "sourceId": "anct-massifs-2016"})
        features.append({"id": mid, "frame": frame["frame"], "label": label, "polygons": [[[round(c) for p in r for c in p] for r in rings]]})
        print(f"  {name:<20} {len(members[mid]):5d} communes, {len(rings):3d} anneaux, {sum(len(r) for r in rings):5d} points, {' '.join(codes)}")

    for pid, name, mid, item, revision in PEAKS:
        if item is None:
            print(f"ERREUR : {pid} sans élément Wikidata")
            return 1
        lon, lat, altitude = wikidata_peak(item, revision)
        # Le sommet se trouve dans le département dont le contour (non simplifié) le contient.
        dep = next((d["DEP"] for d in departments if any(point_in_rings(p, key(lon, lat)[0], key(lon, lat)[1]) for p in units[d["DEP"]])), None)
        if dep is None:  # sommet sur la frontière (mont Blanc) : le département le plus proche dans le massif
            dep = min(departments_of[mid], key=lambda c: min(abs(key(lon, lat)[0] - x) + abs(key(lon, lat)[1] - y) for p in units[c] for r in p for x, y in r))
        frame = frames[dep]
        x, y = frame["to_map"](lon, lat)
        if frame["frame"] != features[[m[0] for m in MASSIFS].index(mid)]["frame"] or not point_in_rings(massif_rings[mid], x, y):
            print(f"ERREUR : {name} n'est pas dans le massif {mid}")
            return 1
        entities.append({"id": pid, "type": "peak", "name": name, "aliases": ALIASES.get(pid, []), "parentId": mid, "overseas": len(dep) == 3, "altitudeM": round(altitude),
                         "departmentIds": [f"DEP-{dep}"], "wikidata": f"{item}@{revision}", "sourceId": "wikidata"})
        features.append({"id": pid, "kind": "point", "frame": frame["frame"], "point": [round(x), round(y)]})
        print(f"  {name:<24} {round(altitude):5d} m  {dep}")

    seen = {}
    for e in entities:
        for n in [e["name"]] + e["aliases"]:
            k = normalized(n)
            if seen.get(k, e["id"]) != e["id"]:
                print(f"ERREUR : « {n} » se confond avec {seen[k]}")
                return 1
            seen[k] = e["id"]

    catalog = {"schemaVersion": 1, "sources": SOURCES}
    text = (json.dumps(catalog, ensure_ascii=False)[:-1] + ',\n"entities": [\n'
            + ",\n".join(json.dumps(e, ensure_ascii=False, separators=(", ", ": ")) for e in entities) + "\n]}\n")
    (OUT / "montagnes.json").write_text(text, encoding="utf-8", newline="\n")
    header = {"schemaVersion": 1, "sourceId": "anct-massifs-2016", "projection": geometry["projection"], "unitKm": geometry["unitKm"],
              "yAxis": "down", "bounds": geometry["bounds"]}
    text = (json.dumps(header, ensure_ascii=False)[:-1] + ',\n"features":[\n'
            + ",\n".join(json.dumps(f, separators=(",", ":")) for f in features) + "\n]}\n")
    (OUT / "montagnes.geometry.json").write_text(text, encoding="utf-8", newline="\n")
    print(f"{len(MASSIFS)} massifs, {len(PEAKS)} sommets, montagnes.geometry.json {(OUT / 'montagnes.geometry.json').stat().st_size // 1024} Ko")
    return 0


if __name__ == "__main__":
    sys.exit(main())
