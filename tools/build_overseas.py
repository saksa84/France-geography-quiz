"""Produit les données des collectivités d'outre-mer (version 3).

Usage (depuis la racine du projet, après tools/download_sources.py et tools/build_geo.py) :
    python -X utf8 tools/build_overseas.py

Entrées (tools/raw/, voir docs/data-sources.md) :
- v_comer_2026.csv : INSEE, COG 2026, collectivités d'outre-mer (numéro, nom) ;
- departements-100m.geojson.gz : IGN ADMIN EXPRESS COG 2026 généralisé par Etalab, qui contient aussi 975 à 989 ;
- wikidata/*.json : chaque collectivité (propriété « capitale », P36) et son chef-lieu (coordonnées), révisions fixées.

Sorties (app/src/main/assets/geo/) :
- outre-mer.json : les 6 collectivités (numéro, chef-lieu, statut) et leurs chefs-lieux ;
- outre-mer.geometry.json : une seconde rangée de cartouches sous ceux des départements d'outre-mer.

Seule la bibliothèque standard de Python est utilisée.
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_mountains import SOURCES as MOUNTAIN_SOURCES  # noqa: E402
from build_geo import (GRID_KM, OUT, OVERSEAS_CODES, RAW, SOURCES as GEO_SOURCES, build_frames, department_units,  # noqa: E402
                       douglas_peucker, label_point, local_projection, normalized, place_inside, read_csv, ring_area)

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

TOLERANCE = 1.0  # 100 m à l'échelle de la métropole : les îles de Polynésie sont minuscules dans leur cartouche
MIN_RING = 0.5


def wikidata_files():
    """Fichiers à télécharger (download_sources.py) : chaque collectivité et son chef-lieu, à leur révision."""
    files = {}
    for _, item, rev, _, _, capital, capital_rev, _ in COLLECTIVITIES:
        files[f"wikidata/{item}-{rev}.json"] = f"https://www.wikidata.org/wiki/Special:EntityData/{item}.json?revision={rev}"
        files[f"wikidata/{capital}-{capital_rev}.json"] = f"https://www.wikidata.org/wiki/Special:EntityData/{capital}.json?revision={capital_rev}"
    return files


def entity(item, rev):
    return json.loads((WIKIDATA_DIR / f"{item}-{rev}.json").read_text(encoding="utf-8"))["entities"][item]


def main() -> int:
    names = {r["COMER"]: r["LIBELLE"] for r in read_csv("v_comer_2026.csv")}
    departments = read_csv("v_departement_2026.csv")
    codes = [c[0] for c in COLLECTIVITIES]
    units = department_units({d["DEP"] for d in departments} | set(codes))
    missing = [c for c in codes if c not in units or c not in names]
    if missing:
        print(f"ERREUR : collectivités sans contour ou sans nom : {missing}")
        return 1
    _, frame_list, _ = build_frames({k: v for k, v in units.items() if k not in codes}, departments)
    mx0, _, mx1, _ = frame_list[0]["bounds_exact"]
    drom_bottom = max(f["bounds"][3] for f in frame_list if f["id"] in OVERSEAS_CODES)

    # Seconde rangée de cartouches, sous ceux des départements d'outre-mer, sur toute la largeur de la métropole.
    gap = (mx1 - mx0) * 0.02
    box = ((mx1 - mx0) - gap * (len(codes) - 1)) / len(codes)
    geometry = json.loads((OUT / "map.geometry.json").read_text(encoding="utf-8"))
    frames, features, entities = [], [], []
    for index, (code, item, rev, capital_name, capital_aliases, capital, capital_rev, status) in enumerate(COLLECTIVITIES):
        # Le chef-lieu doit être la « capitale » (P36) de la collectivité dans Wikidata.
        claims = [c["mainsnak"]["datavalue"]["value"]["id"] for c in entity(item, rev)["claims"].get("P36", []) if c["rank"] != "deprecated"]
        if claims != [capital]:
            print(f"ERREUR : {code} a pour capitale {claims} dans Wikidata, pas {capital}")
            return 1
        coord = entity(capital, capital_rev)["claims"]["P625"][0]["mainsnak"]["datavalue"]["value"]

        lonlat = [(lon / 1e7, lat / 1e7) for p in units[code] for r in p for lon, lat in r]
        lon_c = (min(p[0] for p in lonlat) + max(p[0] for p in lonlat)) / 2
        lat_c = (min(p[1] for p in lonlat) + max(p[1] for p in lonlat)) / 2
        local = local_projection(lon_c, lat_c)
        km = [local(lon, lat) for lon, lat in lonlat]
        extent = max(max(x for x, _ in km) - min(x for x, _ in km), max(y for _, y in km) - min(y for _, y in km))
        scale = (box * 0.86 * GRID_KM) / extent
        bx0, by0 = mx0 + index * (box + gap), drom_bottom + gap
        cx, cy = bx0 + box / 2, by0 + box / 2

        def to_map(lon, lat):
            x, y = local(lon, lat)
            return cx + x * scale / GRID_KM, cy - y * scale / GRID_KM

        polygons = []
        for polygon in units[code]:
            rings = []
            for ring in polygon:
                projected = [to_map(lon / 1e7, lat / 1e7) for lon, lat in ring]
                simple = douglas_peucker(projected + projected[:1], TOLERANCE)[:-1]
                if len(simple) >= 3 and ring_area(simple) >= MIN_RING:
                    rings.append(simple)
            if rings:
                polygons.append(rings)
        (px, py), moved = place_inside(polygons, *to_map(coord["longitude"], coord["latitude"]), margin=1)
        frame_id = code
        frames.append({"id": frame_id, "bounds": [round(bx0), round(by0), round(bx0 + box), round(by0 + box)], "scale": round(scale, 3)})
        features.append({"id": f"COM-{code}", "frame": frame_id, "label": label_point(polygons),
                         "polygons": [[[round(c, 1) for p in r for c in p] for r in poly] for poly in polygons]})
        features.append({"id": f"CL-{code}", "kind": "point", "frame": frame_id, "point": [round(px, 1), round(py, 1)]})
        entities.append({"id": f"COM-{code}", "type": "collectivity", "name": names[code], "code": code, "capitalId": f"CL-{code}",
                         "overseas": True, "note": status, "noteSource": CONSTITUTION, "sourceId": GEO_SOURCES[0]["id"]})
        entities.append({"id": f"CL-{code}", "type": "prefecture", "name": capital_name, "aliases": capital_aliases, "parentId": f"COM-{code}",
                         "wikidata": f"{capital}@{capital_rev}", "sourceId": WIKIDATA_SOURCE["id"]})
        print(f"  {code} {names[code]:<26} échelle ×{scale:6.3f}, {len(polygons):3d} îles, chef-lieu {capital_name}"
              + (f" (rentré de {moved * GRID_KM / scale:.1f} km)" if moved else ""))

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
    all_bounds = [f["bounds"] for f in frames]
    bounds = [geometry["bounds"][0], geometry["bounds"][1], max(geometry["bounds"][2], max(b[2] for b in all_bounds)),
              max(geometry["bounds"][3], max(b[3] for b in all_bounds))]
    header = {"schemaVersion": 1, "sourceId": GEO_SOURCES[1]["id"],
              "projection": {"overseas": "équirectangulaire locale par collectivité, mise à l'échelle de son cartouche"},
              "unitKm": GRID_KM, "yAxis": "down", "bounds": bounds, "frames": frames}
    text = (json.dumps(header, ensure_ascii=False)[:-1] + ',\n"features":[\n'
            + ",\n".join(json.dumps(f, separators=(",", ":")) for f in features) + "\n]}\n")
    (OUT / "outre-mer.geometry.json").write_text(text, encoding="utf-8", newline="\n")
    print(f"{len(COLLECTIVITIES)} collectivités, outre-mer.geometry.json {(OUT / 'outre-mer.geometry.json').stat().st_size // 1024} Ko")
    return 0


if __name__ == "__main__":
    sys.exit(main())
