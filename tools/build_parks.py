"""Produit les données des parcs nationaux (version 3).

Usage (depuis la racine du projet, après tools/download_sources.py et tools/build_geo.py) :
    python -X utf8 tools/build_parks.py

Entrée : tools/raw/pnx-coeur-aa-ama.geojson, Parcs nationaux de France (OFB), « Contours des 11 Parcs
nationaux de France », Licence Ouverte 2.0 : cœurs terrestres (CT) et marins (CM), aires d'adhésion,
aires maritimes adjacentes, avec décret, date de création et surface.

Sorties (app/src/main/assets/geo/) :
- parcs.json : les 11 parcs (date de création, surface du cœur, départements, décret) ;
- parcs.geometry.json : leurs cœurs (terrestre et marin), dans le cadre de leur territoire.

Seule la bibliothèque standard de Python est utilisée.
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_geo import OUT, RAW, build_frames, department_units, douglas_peucker, key, label_point, normalized, point_in_rings, read_csv, ring_area  # noqa: E402

RAW_FILE = "pnx-coeur-aa-ama.geojson"
URL = "https://static.data.gouv.fr/resources/contours-des-11-parcs-nationaux-de-france/20260211-094019/pnx-coeur-aa-ama.geojson"
PAGE = "https://www.data.gouv.fr/datasets/contours-des-11-parcs-nationaux-de-france"
SOURCE = {"id": "pnf-contours-2026", "name": "Parcs nationaux de France (OFB) : contours des 11 parcs nationaux", "license": "Licence Ouverte Etalab 2.0",
          "url": PAGE, "version": "11 février 2026"}

# Parcs : identifiant stable, code du fichier (suffixe de « id_local »), nom officiel, nom court (alias).
PARKS = [
    ("PN-VANOISE", "PNV", "Parc national de la Vanoise", "Vanoise"),
    ("PN-PORTCROS", "PNPC", "Parc national de Port-Cros", "Port-Cros"),
    ("PN-PYRENEES", "PNP", "Parc national des Pyrénées", "Pyrénées"),
    ("PN-CEVENNES", "PNC", "Parc national des Cévennes", "Cévennes"),
    ("PN-ECRINS", "PNE", "Parc national des Écrins", "Écrins"),
    ("PN-MERCANTOUR", "PNM", "Parc national du Mercantour", "Mercantour"),
    ("PN-GUADELOUPE", "PNG", "Parc national de la Guadeloupe", "Guadeloupe"),
    ("PN-GUYANE", "PAG", "Parc amazonien de Guyane", "Parc amazonien"),
    ("PN-REUNION", "PNRUN", "Parc national de La Réunion", "La Réunion"),
    ("PN-CALANQUES", "PNCAL", "Parc national des Calanques", "Calanques"),
    ("PN-FORETS", "PNFOR", "Parc national de Forêts", "Forêts"),
]

MIN_RING = 4  # anneaux de moins de 4 unités² (0,04 km² en métropole) : écartés
GRID = 10  # pas de la grille (1 km) pour savoir dans quels départements se trouve le cœur terrestre
MIN_SHARE = 0.01  # un département compte s'il porte au moins 1 % du cœur terrestre

def polygons_of(geometry):
    return geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]


def main() -> int:
    features = json.loads((RAW / RAW_FILE).read_text(encoding="utf-8"))["features"]
    by_local = {f["properties"]["id_local"]: f for f in features}
    departments = read_csv("v_departement_2026.csv")
    units = department_units({d["DEP"] for d in departments})
    frames, _, _ = build_frames(units, departments)
    bounds = {d: (min(x for p in polys for r in p for x, _ in r), min(y for p in polys for r in p for _, y in r),
                  max(x for p in polys for r in p for x, _ in r), max(y for p in polys for r in p for _, y in r))
              for d, polys in units.items()}

    geometry = json.loads((OUT / "map.geometry.json").read_text(encoding="utf-8"))
    order = [f["id"] for f in geometry["features"]]

    entities, shapes = [], []
    for pid, code, name, short in PARKS:
        land = by_local.get(f"CT_{code}")
        sea = by_local.get(f"CM_{code}")
        if land is None:
            print(f"ERREUR : cœur terrestre CT_{code} absent")
            return 1
        props = land["properties"]
        # Département de chaque point d'une grille de 1 km posée sur le cœur terrestre (contours non simplifiés).
        land_polys = [[[key(lon, lat) for lon, lat in ring] for ring in poly] for poly in polygons_of(land["geometry"])]
        xs = [x for p in land_polys for r in p for x, _ in r]
        ys = [y for p in land_polys for r in p for _, y in r]
        # Pas d'au moins 1 km, élargi pour les grands parcs (Guyane) : quelques milliers de points au plus.
        step = max(int(GRID * 0.1 / 111 * 1e7), int(((max(xs) - min(xs)) * (max(ys) - min(ys)) / 3000) ** 0.5))
        count, total = {}, 0
        for gx in range(min(xs), max(xs), step):
            for gy in range(min(ys), max(ys), step):
                if not any(point_in_rings(p, gx, gy) for p in land_polys):
                    continue
                total += 1
                dep = next((d for d, polys in units.items()
                            if bounds[d][0] <= gx <= bounds[d][2] and bounds[d][1] <= gy <= bounds[d][3]
                            and any(point_in_rings(p, gx, gy) for p in polys)), None)
                if dep:
                    count[dep] = count.get(dep, 0) + 1
        crossed = [d for d, n in count.items() if n >= max(1, MIN_SHARE * total)]
        if not crossed:
            print(f"ERREUR : {pid} hors de tout département")
            return 1
        frame = frames[crossed[0]]
        if any(frames[d]["frame"] != frame["frame"] for d in crossed):
            print(f"ERREUR : {pid} à cheval sur deux cadres")
            return 1

        def project(part):
            out = []
            for poly in polygons_of(part["geometry"]):
                for ring in poly:
                    projected = [frame["to_map"](lon, lat) for lon, lat in ring[:-1]]
                    simple = douglas_peucker(projected + projected[:1], frame["tolerance"])[:-1]
                    if len(simple) >= 3 and ring_area(simple) >= MIN_RING:
                        out.append(simple)
            return out

        land_rings = project(land)
        rings = land_rings + (project(sea) if sea else [])
        area_ha = props["surf_ha"] + (sea["properties"]["surf_ha"] if sea else 0)
        entities.append({
            "id": pid, "type": "park", "name": name, "aliases": [short],
            "overseas": frame["frame"] != "metro",
            "areaKm2": round(area_ha / 100),
            "established": props["date_creation"],
            "departmentIds": sorted((f"DEP-{d}" for d in crossed), key=order.index),
            "note": f"{props['decret'].strip()}.", "noteSource": PAGE,
            "sourceId": SOURCE["id"],
        })
        # Nom écrit sur la terre (le cœur marin de Port-Cros ou des Calanques est bien plus grand que la terre).
        label = label_point([[r] for r in land_rings or rings])
        shapes.append({"id": pid, "frame": frame["frame"], "label": label, "polygons": [[[round(c) for p in r for c in p] for r in rings]]})
        print(f"  {short:<15} créé le {props['date_creation']}, cœur {round(area_ha / 100):6d} km², {len(rings):3d} anneaux, {' '.join(crossed)}")

    seen = {}
    for e in entities:
        for n in [e["name"]] + e["aliases"]:
            k = normalized(n)
            if seen.get(k, e["id"]) != e["id"]:
                print(f"ERREUR : « {n} » se confond avec {seen[k]}")
                return 1
            seen[k] = e["id"]

    text = (json.dumps({"schemaVersion": 1, "sources": [SOURCE]}, ensure_ascii=False)[:-1] + ',\n"entities": [\n'
            + ",\n".join(json.dumps(e, ensure_ascii=False, separators=(", ", ": ")) for e in entities) + "\n]}\n")
    (OUT / "parcs.json").write_text(text, encoding="utf-8", newline="\n")
    header = {"schemaVersion": 1, "sourceId": SOURCE["id"], "projection": geometry["projection"], "unitKm": geometry["unitKm"],
              "yAxis": "down", "bounds": geometry["bounds"]}
    text = (json.dumps(header, ensure_ascii=False)[:-1] + ',\n"features":[\n'
            + ",\n".join(json.dumps(s, separators=(",", ":")) for s in shapes) + "\n]}\n")
    (OUT / "parcs.geometry.json").write_text(text, encoding="utf-8", newline="\n")
    print(f"{len(entities)} parcs, parcs.geometry.json {(OUT / 'parcs.geometry.json').stat().st_size // 1024} Ko")
    return 0


if __name__ == "__main__":
    sys.exit(main())
