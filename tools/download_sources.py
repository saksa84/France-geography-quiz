"""Télécharge les données sources du projet et vérifie leur empreinte SHA-256.

Usage (depuis la racine du projet) :
    python -X utf8 tools/download_sources.py

Les fichiers vont dans tools/raw/ (ignoré par Git : plusieurs Mo, régénérables).
Une empreinte différente signifie que le fichier a changé chez l'éditeur : on s'arrête plutôt
que de construire la carte sur des données non vérifiées. Après vérification du nouveau
fichier, mettre à jour l'empreinte ici et dans docs/data-sources.md.

Sources (détail et licences : docs/data-sources.md) :
- INSEE, Code officiel géographique au 1er janvier 2026 : départements, régions, chefs-lieux,
  noms des communes. Licence Ouverte Etalab 2.0.
- Etalab, contours administratifs 2026 (dérivés d'IGN ADMIN EXPRESS COG 2026, généralisés
  à 100 m) : contours des départements, position des mairies. Licence Ouverte Etalab 2.0.
- INSEE, populations de référence 2023 et base du comparateur de territoires (superficies).
  Licence Ouverte Etalab 2.0.
- Natural Earth 5.1.2 : pays voisins, en fond de carte. Domaine public.
- Sandre, BD TOPAGE 2025 (OFB, IGN) : les cours d'eau de tools/build_rivers.py, demandés au service
  WFS du Sandre (réponse triée, donc identique d'un téléchargement à l'autre). Licence Ouverte 2.0.
"""

import hashlib
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_mountains import MASSIF_FILE, wikidata_files  # noqa: E402
from build_overseas import wikidata_files as overseas_wikidata_files  # noqa: E402
from build_parks import RAW_FILE as PARKS_FILE, URL as PARKS_URL  # noqa: E402
from build_rivers import ESTUARY_FILE, RAW_FILE as TOPAGE_FILE, estuary_url, topage_url  # noqa: E402

INSEE = "https://www.insee.fr/fr/statistiques/fichier/8740222"
ETALAB = "https://etalab-datasets.geo.data.gouv.fr/contours-administratifs/2026/geojson"
NATURAL_EARTH = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson"

FILES = {
    "v_departement_2026.csv": (f"{INSEE}/v_departement_2026.csv", "513900ca23edc682ef9b912d7bfc317129eac3a7d71ff19bc22b40ea4838e6eb"),
    "v_region_2026.csv": (f"{INSEE}/v_region_2026.csv", "d903566d9c0d263389d279baea6761917b7460749a07bec8ba37dd8f2ff7d98a"),
    "v_commune_2026.csv": (f"{INSEE}/v_commune_2026.csv", "7be1153de3a63df48fa0ca77f8490c8a0c5c9c4eb12f5cec1ca7f70b0237b1d3"),
    "departements-100m.geojson.gz": (f"{ETALAB}/departements-100m.geojson.gz", "c330276ba97a2c273c7b0508db9e47f3025d90b1734faf454fd089b87d9e96ea"),
    "mairies.geojson.gz": (f"{ETALAB}/mairies.geojson.gz", "0a4f4977c849d87543b30f738d4aefe5ebf10d3b5b201aa84e8b4a75e8f61618"),
    "pop-ref-2023-ensemble.zip": ("https://www.insee.fr/fr/statistiques/fichier/8680726/ensemble.zip", "5174fc225398a4c072fb8bb5298b7ccf30c017834c7a4171f44787379d83b3f5"),
    "comparateur_csv.zip": ("https://www.insee.fr/fr/statistiques/fichier/2521169/comparateur_csv.zip", "6d7fff2db2be0fe499ae18426c807e82ab458bf21941d627dd64e427816254b0"),
    "ne_10m_admin_0_countries.geojson": (f"{NATURAL_EARTH}/ne_10m_admin_0_countries.geojson", "239eec57ac17f100a11e2536cffc56752c318b50ae765b0918ff7aab4ce8f255"),
    TOPAGE_FILE: (topage_url(), "96c88365a9817ec66a1e32db9cc4383eafd1953c24e6d8563e9aae0d4515ed1d"),
    ESTUARY_FILE: (estuary_url(), "fbadc5f243b518b836e885d94da12545e4245a449a8ba95a0b4838a3d22521f1"),
    "communes-100m.geojson.gz": (f"{ETALAB}/communes-100m.geojson.gz", "4530cbf87a3af387c2da95935376f74b93eabd0a7595eacc5b7e0ed94a0b3778"),
    "v_mvt_commune_2026.csv": (f"{INSEE}/v_mvt_commune_2026.csv", "8b339fad8232e97233af3fd9468715af845e2cf8b14cb67b24d9a301ef8645f1"),
    MASSIF_FILE: ("https://static.data.gouv.fr/resources/les-perimetres-de-massifs/20210907-123330/diffusion-zonages-massifs-cog2021.xls", "3b876dc86e07e53a8529e6561096299fd98fbb2a909b15acb853bf7b4aa2f434"),
}
FILES[PARKS_FILE] = (PARKS_URL, "08079d80a0badec10304044a883ed00630f5bfd7a9f23d28e0bec64eedd597c3")
FILES["v_comer_2026.csv"] = (f"{INSEE}/v_comer_2026.csv", "d1f08ba101abf50c0a5db5cf7b24a50397bf68706aa658e4027a4fb933f059ed")
# Sommets, collectivités d'outre-mer et leurs chefs-lieux : une révision Wikidata ne change jamais, pas d'empreinte à vérifier.
FILES.update({name: (url, None) for name, url in {**wikidata_files(), **overseas_wikidata_files()}.items()})

RAW_DIR = Path(__file__).resolve().parent / "raw"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    RAW_DIR.mkdir(exist_ok=True)
    for name, (url, expected) in FILES.items():
        target = RAW_DIR / name
        target.parent.mkdir(exist_ok=True)
        if target.exists() and expected in (None, sha256(target)):
            print(f"déjà présent : {name}")
            continue
        print(f"téléchargement : {name}")
        # Wikimedia refuse les requêtes sans User-Agent qui identifie l'outil.
        request = urllib.request.Request(url, headers={"User-Agent": "france-geography-quiz/0.1 (github.com/saksa84/France-geography-quiz)"})
        with urllib.request.urlopen(request, timeout=300) as response:
            target.write_bytes(response.read())
        actual = sha256(target)
        if expected is not None and actual != expected:
            print(f"ERREUR : empreinte inattendue pour {name}\n  attendue {expected}\n  obtenue  {actual}")
            return 1
    print(f"OK : {len(FILES)} fichiers vérifiés dans {RAW_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
