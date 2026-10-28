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
| `sandre-bd-topage-2025` | Sandre, BD TOPAGE® 2025 (OFB, IGN), couche `CoursEau_FXX_Topage2025`, demandée au service WFS <https://services.sandre.eaufrance.fr/geo/topage2025> (une seule requête, filtrée sur les codes `CdOH` des 47 cours d'eau, triée par code : la réponse est identique d'un téléchargement à l'autre) | 2025 | Licence Ouverte Etalab 2.0 (fiche data.gouv.fr « BD Topage® ») | `topage2025-cours-eau.geojson` (`96c88365…d1d`) ; couche `PlanEau_FXX_Topage2025`, plan d'eau « la Gironde » (estuaire) : `topage2025-estuaires.geojson` (`fbadc5f2…1f1`) |
| Wikipédia (rattachements) | Fleuve dans lequel se jette chaque rivière, vérifié dans l'introduction de son article ; titre et révision de chaque article dans la table `RIVERS` de `tools/build_rivers.py` | consultés le 27 octobre 2026 | CC BY-SA 4.0 (simple fait géographique, non reproduit) | — |
| `ign-admin-express-cog-2026` (communes) | Même source que les contours des départements : contours des communes, pour dessiner les massifs | 2026, généralisation 100 m | Licence Ouverte Etalab 2.0 | `communes-100m.geojson.gz` (`4530cbf8…778`) |
| `insee-cog-2026` (mouvements) | INSEE, COG 2026 : mouvements des communes, pour passer des communes 2021 aux communes 2026 | millésime 2026 | Licence Ouverte Etalab 2.0 | `v_mvt_commune_2026.csv` (`8b339fad…5f1`) |
| `anct-massifs-2016` | ANCT, Observatoire des territoires : communes classées en zones de massif (décret n° 2004-69 du 16 janvier 2004 modifié par le décret du 8 septembre 2016), en COG 2021 — <https://www.data.gouv.fr/datasets/les-perimetres-de-massifs> | publié le 7 septembre 2021 | Licence Ouverte | `diffusion-zonages-massifs-cog2021.xls` (`3b876dc8…434`) |
| `wikidata` | Wikidata : coordonnées (P625) et altitude (P2044) des 30 sommets ; élément et révision de chacun dans la table `PEAKS` de `tools/build_mountains.py` et dans le champ `wikidata` des données | révisions consultées le 28 octobre 2026 | CC0 1.0 (domaine public) | `wikidata/<élément>-<révision>.json` (une révision ne change jamais : pas d'empreinte) |
| `pnf-contours-2026` | Parcs nationaux de France (OFB) : « Contours des 11 Parcs nationaux de France » — <https://www.data.gouv.fr/datasets/contours-des-11-parcs-nationaux-de-france> | 11 février 2026 | Licence Ouverte Etalab 2.0 | `pnx-coeur-aa-ama.geojson` (`08079d80…7c3`) |
| `insee-cog-2026` (outre-mer) | INSEE, COG 2026 : collectivités d'outre-mer (numéro, nom) | millésime 2026 | Licence Ouverte Etalab 2.0 | `v_comer_2026.csv` (`d1f08ba1…9ed`) |
| Constitution | Article 72-3 (statut des collectivités d'outre-mer et de la Nouvelle-Calédonie), cité par la note de chaque collectivité — <https://www.conseil-constitutionnel.fr/le-bloc-de-constitutionnalite/texte-integral-de-la-constitution-du-4-octobre-1958-en-vigueur> | consulté le 28 octobre 2026 | Texte officiel | — |
| `natural-earth-5.1.2` | Natural Earth, Admin 0 – Countries, 1:10m — <https://www.naturalearthdata.com> | 5.1.2 | Domaine public | `ne_10m_admin_0_countries.geojson` (`239eec57…255`) |

Empreintes complètes dans `tools/download_sources.py`.

**Attribution** (obligatoire pour la Licence Ouverte) : faite dans le README et dans Paramètres → À propos : « Données : INSEE, Code officiel géographique 2026 ; IGN, ADMIN EXPRESS COG 2026, contours généralisés par Etalab. Licence Ouverte Etalab 2.0. Pays voisins : Made with Natural Earth. Cours d'eau : Sandre, BD TOPAGE 2025 (OFB, IGN). Massifs : ANCT. Parcs nationaux : Parcs nationaux de France (OFB). Sommets et chefs-lieux d'outre-mer : Wikidata (CC0). » Les fiches, reformulées d'après Wikipédia, portent la mention « d'après Wikipédia en français (CC BY-SA 4.0) » ; le registre `docs/verification-fiches.md` donne le lien de chaque révision utilisée.

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
| `geo/cours-eau.json` | 47 cours d'eau : nom, alias avec l'article (« la Loire »), fleuve dans lequel ils se jettent (`parentId`), départements traversés, codes BD TOPAGE | BD TOPAGE 2025 ; Wikipédia (rattachements) | Produit par `python -X utf8 tools/build_rivers.py` (après `build_geo.py`). Liste, codes et rattachements : table `RIVERS` du script. Alias : nom BD TOPAGE sans « Fleuve » ni « Rivière ». Départements traversés : au moins 5 km de cours dans le contour simplifié du département, mesurés tous les 500 m sur le tracé complet. |
| `geo/cours-eau.geometry.json` | Tracés des cours d'eau ; estuaire de la Gironde (`water`) | BD TOPAGE 2025 | Même projection que la métropole (Lambert-93, unités de 100 m). **Cours principal seulement** : BD TOPAGE décrit aussi les bras secondaires (plus de 100 tronçons pour la Seine) ; le script relie les tronçons en graphe et garde le plus long des plus courts chemins, de la source à l'embouchure, d'un seul tenant. La Somme et l'Escaut assemblent ainsi deux objets BD TOPAGE (cours amont et partie canalisée, reliés à moins de 1 km). Puis Douglas-Peucker, tolérance 250 m. Point d'étiquette : milieu du cours principal. Estuaire : les contours des départements recouvrent l'estuaire de la Gironde (limites communales au milieu de l'eau) ; son plan d'eau BD TOPAGE, simplifié à 250 m, est dessiné en mer par-dessus (îles de moins de 1 km² écartées). |
| `geo/montagnes.json` | 9 massifs (départements où ils ont au moins une commune) et 30 sommets (altitude, massif, département, élément Wikidata) | ANCT ; communes Etalab ; COG 2026 ; Wikidata | Produit par `python -X utf8 tools/build_mountains.py` (après `build_geo.py`). Massif de chaque commune 2026 : celui de la commune 2021 de même code, de ses communes déléguées ou associées, ou des communes dont elle est issue depuis 2021 (mouvements INSEE) ; « partiellement » compte comme dedans. Altitude : valeur « préférée » de Wikidata, sinon la plus récente (P585), arrondie au mètre. Département d'un sommet : celui dont le contour le contient (le plus proche pour un sommet frontalier). Alias : table `ALIASES` du script. |
| `geo/montagnes.geometry.json` | Contours des massifs, points des sommets | Communes Etalab ; Wikidata | Union des communes de chaque massif (arêtes communes supprimées : les contours Etalab sont topologiques), projetée dans son cadre (métropole ou cartouche, fonction `build_frames` de `build_geo.py`), Douglas-Peucker à 250 m, anneaux de moins de 1 km² écartés. Chaque sommet est vérifié dans son massif. |
| `geo/parcs.json` | 11 parcs nationaux : nom, nom court (alias), date de création, surface du cœur, départements, décret (note) | Parcs nationaux de France (OFB) | Produit par `python -X utf8 tools/build_parks.py`. Date : celle du cœur terrestre (décret de création). Surface : somme des surfaces officielles (`surf_ha`) du cœur terrestre et du cœur marin. Départements : grille d'au moins 1 km posée sur le cœur terrestre (contours non simplifiés), un département compte à partir de 1 % des points. |
| `geo/parcs.geometry.json` | Cœurs des parcs (terrestre et marin) | Parcs nationaux de France (OFB) | Projetés dans le cadre de leur territoire (`build_frames` de `build_geo.py`), Douglas-Peucker à 250 m, anneaux de moins de 0,04 km² écartés. L'aire d'adhésion et l'aire maritime adjacente ne sont pas dessinées. Point d'étiquette sur la partie terrestre. |
| `geo/outre-mer.json` | 6 collectivités d'outre-mer (numéro, nom, chef-lieu, statut en note) et leurs chefs-lieux | COG 2026 ; Wikidata ; Constitution | Produit par `python -X utf8 tools/build_overseas.py`. Identifiants `COM-<numéro>` et `CL-<numéro>`. Chef-lieu : la « capitale » (P36) de la collectivité dans Wikidata, vérifiée par le script, avec ses coordonnées (révisions dans la table `COLLECTIVITIES`). Écartés : 984 (Terres australes) et 989 (Clipperton). |
| `geo/outre-mer.geometry.json` | Seconde rangée de 6 cartouches, contours des collectivités, chefs-lieux | Contours IGN/Etalab (fichier des départements, qui contient 975 à 989) ; Wikidata | Projection équirectangulaire locale par collectivité, à l'échelle de son cartouche (×0,07 pour la Polynésie, ×12 pour Saint-Barthélemy). Douglas-Peucker à 100 m (à l'échelle de la métropole) pour garder les îles ; anneaux de moins de 0,5 unité² écartés (65 îles gardées en Polynésie). Chef-lieu ramené à l'intérieur du contour simplifié si besoin. |
| `game/levels.json` | Niveaux | Écrit à la main | Critères (régions, tranches de numéros, outre-mer, bassins), pas de listes en dur. |

Autres fichiers générés :

| Fichier | Script | Remarque |
|---|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | `tools/build_icon.py` | Silhouette de la métropole, tirée de `map.geometry.json`. |
| `app/src/main/res/raw/answer_correct.wav`, `answer_wrong.wav` | `tools/build_sounds.py --choisir correct=marimba erreur=quinte` | Sons synthétisés : aucune licence à gérer. « quinte » reproduit les caractéristiques mesurées (notes, timbre, enveloppe) d'un son de Pixabay qui n'est **pas** intégré : la licence Pixabay interdit de redistribuer le fichier seul. |

## Reconstruire les données

```bash
python -X utf8 tools/download_sources.py
python -X utf8 tools/build_geo.py
python -X utf8 tools/build_rivers.py
python -X utf8 tools/build_mountains.py
python -X utf8 tools/build_parks.py
python -X utf8 tools/build_overseas.py
python -X utf8 tools/build_icon.py
```

Puis lancer les tests (`docs/build.md`) : plusieurs tests lisent les vrais fichiers de `assets`.
