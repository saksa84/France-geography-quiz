# Sources des données et licences

Registre de toutes les données intégrées à l'application : source, version, licence, transformations et script qui produit chaque fichier. Les fichiers bruts sont téléchargés dans `tools/raw/` (ignoré par Git) par `tools/download_sources.py`, qui vérifie leur empreinte SHA-256 : un fichier modifié chez l'éditeur arrête la construction.

## Sources

| Identifiant (`sourceId`) | Source | Version | Licence | Fichiers bruts (SHA-256) |
|---|---|---|---|---|
| `insee-cog-2026` | INSEE, Code officiel géographique au 1er janvier 2026 — <https://www.insee.fr/fr/information/8740222> (aussi sur data.gouv.fr, jeu « Code officiel géographique ») | millésime 2026 | Licence Ouverte Etalab 2.0 | `v_departement_2026.csv` (`513900ca…6eb`), `v_region_2026.csv` (`d903566d…98a`), `v_commune_2026.csv` (`7be1153d…7b3`) |
| `ign-admin-express-cog-2026` | IGN, ADMIN EXPRESS COG 2026, contours généralisés par Etalab — <https://etalab-datasets.geo.data.gouv.fr/contours-administratifs/2026/> | 2026, généralisation 100 m | Licence Ouverte Etalab 2.0 | `departements-100m.geojson.gz` (`c330276b…ea`), `mairies.geojson.gz` (`0a4f4977…618`) |
| `insee-populations-2023` | INSEE, populations de référence 2023 (population municipale), en vigueur au 1er janvier 2026 — <https://www.insee.fr/fr/statistiques/8680726> | 2023 | Licence Ouverte Etalab 2.0 | `pop-ref-2023-ensemble.zip` (`5174fc22…3f5`) |
| `insee-comparateur-2026` | INSEE, base du comparateur de territoires (mesure « SUP », superficie en km²) — <https://www.insee.fr/fr/statistiques/2521169> | 2026 | Licence Ouverte Etalab 2.0 | `comparateur_csv.zip` (`6d7fff2d…0b0`) |
| Légifrance | Textes de loi cités par les notes administratives (adresse de chaque article dans `noteSource`) — <https://www.legifrance.gouv.fr> | consultés le 27 octobre 2026 | Données publiques | — |
| `natural-earth-5.1.2` | Natural Earth, Admin 0 – Countries, 1:10m — <https://www.naturalearthdata.com> | 5.1.2 | Domaine public | `ne_10m_admin_0_countries.geojson` (`239eec57…255`) |

Empreintes complètes dans `tools/download_sources.py`.

**Attribution** (obligatoire pour la Licence Ouverte) : faite dans le README et dans Paramètres → À propos : « Données : INSEE, Code officiel géographique 2026 ; IGN, ADMIN EXPRESS COG 2026, contours généralisés par Etalab. Licence Ouverte Etalab 2.0. Pays voisins : Made with Natural Earth. » Les fiches, reformulées d'après Wikipédia, portent la mention « d'après Wikipédia en français (CC BY-SA 4.0) » ; le registre `docs/verification-fiches.md` donne le lien de chaque révision utilisée.

**Exclues** : OpenStreetMap (inutile ici), GADM et WDPA (redistribution interdite). L'API geo.api.gouv.fr a été consultée le 25 octobre 2026 pour contrôler les superficies, mais n'est pas utilisée dans les données (réponses non versionnées, millésimes de population mélangés).

## Fichiers produits

Tous par `python -X utf8 tools/build_geo.py` (bibliothèque standard de Python uniquement), sauf mention contraire.

| Fichier (`app/src/main/assets/`) | Contenu | Sources | Transformations |
|---|---|---|---|
| `geo/regions.json` | 18 régions : nom, code INSEE, chef-lieu (`capitalId`), outre-mer, population | COG 2026, populations 2023 | Identifiants stables `REG-xxx` attribués par le script (table `REGION_IDS`). Le chef-lieu de région est le chef-lieu (préfecture) de l'un de ses départements. |
| `geo/departements.json` | 101 départements : numéro, nom, chef-lieu, région, superficie, population, notes administratives, alias | COG 2026 ; populations 2023 ; comparateur (superficie) ; Légifrance (notes) | `DEP-<numéro>`. Superficie et population officielles de l'INSEE, arrondies ; **Mayotte n'a ni l'une ni l'autre** (absente des deux fichiers INSEE). Notes : table `NOTES` du script, chacune avec l'adresse Légifrance de sa source. Alias : abréviations « St / Ste » générées, plus la table `EXTRA_ALIASES` (Réunion, PACA, Région Sud, Évry). |
| `geo/chefs-lieux.json` | 101 chefs-lieux : nom de la commune (avec son article), code INSEE, département | COG 2026 (colonne `CHEFLIEU`, puis `LIBELLE` de la commune) | `CL-<numéro du département>`. |
| `geo/map.geometry.json` | Contours des départements, points des chefs-lieux, cadres, pays voisins | Contours et mairies IGN/Etalab ; Natural Earth | Métropole en Lambert-93 (EPSG:2154, GRS80), unité 100 m, axe y vers le bas. Outre-mer : projection équirectangulaire locale, mise à l'échelle de son cartouche. Simplification de Douglas-Peucker par frontière partagée, tolérance 250 m (à l'échelle de la métropole). Îles < 1 km² supprimées. Chefs-lieux : mairie de la commune chef-lieu, ramenée à 300 m à l'intérieur du contour simplifié si besoin (8 cas, listés par le script). Pays voisins : simplifiés à 1 km et découpés au cadre de la métropole. Collectivités d'outre-mer (975, 977, 978, 984, 986 à 989) écartées. |
| `geo/facts.json` | 101 fiches « En savoir plus » | Wikipédia en français (CC BY-SA 4.0), une révision précise par article ; registre `docs/verification-fiches.md` | Produit par `python -I -X utf8 tools/wiki_facts.py build` à partir de `tools/facts_sources.json` : textes reformulés, chacun accompagné d'extraits ; le script vérifie que chaque extrait figure mot pour mot dans la révision téléchargée (`tools/raw/wiki/`, par `wiki_facts.py fetch`) et écrit le registre. |
| `game/levels.json` | Niveaux | Écrit à la main | Critères (régions, tranches de numéros, outre-mer), pas de listes en dur. |

Autres fichiers générés :

| Fichier | Script | Remarque |
|---|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | `tools/build_icon.py` | Silhouette de la métropole, tirée de `map.geometry.json`. |
| `app/src/main/res/raw/answer_correct.wav`, `answer_wrong.wav` | `tools/build_sounds.py --choisir correct=marimba erreur=quinte` | Sons synthétisés : aucune licence à gérer. « quinte » reproduit les caractéristiques mesurées (notes, timbre, enveloppe) d'un son de Pixabay qui n'est **pas** intégré : la licence Pixabay interdit de redistribuer le fichier seul. |

## Reconstruire les données

```bash
python -X utf8 tools/download_sources.py
python -X utf8 tools/build_geo.py
python -X utf8 tools/build_icon.py
```

Puis lancer les tests (`docs/build.md`) : plusieurs tests lisent les vrais fichiers de `assets`.
