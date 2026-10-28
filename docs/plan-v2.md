# Plan de la version 2 : fleuves et rivières, montagnes

Rédigé le 27 octobre 2026 (fleuves et rivières), complété le 28 octobre 2026 (montagnes). Les deux parties sont faites.

## 1. Objectif

Une catégorie **Fleuves et rivières** avec les mêmes modes que les départements (Apprendre, Trouver, Éliminer, Saisir), et un lien avec les départements : quels cours d'eau traversent un département, quels départements portent le nom d'un cours d'eau.

## 2. Source des tracés

| Source | Verdict |
|---|---|
| **BD TOPAGE 2025** (Sandre, OFB, IGN) | **Retenue.** Référentiel hydrographique officiel qui remplace BD CARTHAGE. Licence Ouverte Etalab 2.0 (fiche data.gouv.fr « BD Topage® »). La couche `CoursEau` donne chaque cours d'eau entier, avec son nom (`TopoOH`) et un code stable (`CdOH`). Précision bien meilleure que notre simplification à 250 m, donc tracés cohérents avec les contours IGN des départements. |
| BD CARTHAGE | Écartée : remplacée par BD TOPAGE, plus mise à jour. |
| Natural Earth (rivières, 1:10 M) | Écartée : trop grossière (les rivières qui forment des limites de département ne tomberaient pas dessus) et liste incomplète des affluents français. |
| OpenStreetMap | Inutile ici (et ODbL). |

Téléchargement : une seule requête au service WFS du Sandre (`topage2025`, couche `CoursEau_FXX_Topage2025`), filtrée sur les 49 codes `CdOH`, triée par code. Deux téléchargements ont donné le même fichier : son empreinte SHA-256 est vérifiée comme pour les autres sources (`tools/download_sources.py`).

## 3. Décisions (à revoir avec moi si besoin)

1. **47 cours d'eau**, tous en métropole : 16 fleuves (Loire, Seine, Rhône, Garonne, Dordogne, Rhin, Meuse, Escaut, Somme, Orne, Vilaine, Charente, Adour, Aude, Hérault, Var) et 31 rivières. Les rivières sont choisies parce qu'elles sont les grands affluents ou parce qu'un département porte leur nom (Ain, Allier, Ardèche, Aube, Aveyron, Cher, Creuse, Doubs, Drôme, Eure, Gers, Indre, Isère, Lot, Marne, Mayenne, Meurthe, Oise, Sarthe, Tarn, Vienne, Yonne…).
2. **Rattachement** : chaque rivière pointe vers le cours d'eau dans lequel elle se jette (`parentId`), vérifié dans l'introduction de son article Wikipédia (révision notée dans `tools/build_rivers.py`). Sous-affluents rattachés à leur rivière (Creuse → Vienne → Loire ; Loir → Sarthe → Maine → Loire).
3. **La Dordogne est un fleuve** : Wikipédia la classe ainsi, car elle forme l'estuaire de la Gironde avec la Garonne. Le niveau « Garonne et Dordogne » les réunit.
4. **Somme et Escaut** : BD TOPAGE les découpe en deux objets (cours amont, partie canalisée) ; on les assemble.
5. **Questions** : seulement le nom. Dans Saisir, « Loire » et « la Loire » sont acceptés (alias tiré de BD TOPAGE).
6. **Fiche** : fleuve ou « se jette dans… », bassin (pour un sous-affluent), affluents présents dans le quiz, départements traversés (au moins 5 km de cours), départements dont le nom contient celui du cours d'eau (mot entier : le Loir est dans Loir-et-Cher, pas dans Loire). Pas de longueur pour l'instant : la longueur mesurée sur BD TOPAGE (bras compris, partie française seulement) ne correspond pas aux longueurs officielles ; à ajouter avec une source citée si on le veut.
7. **Fiche des départements** : nouvelle ligne « Cours d'eau : … » (ceux du quiz qui le traversent).
8. **Niveaux** : par bassin (Loire ; Seine ; Rhône ; Garonne et Dordogne ; Rhin, Meuse, Escaut ; fleuves côtiers), puis les fleuves, les rivières, tous. Nouveaux critères dans `levels.json` : `basinIds`, `mouthsOnly`.
9. **Carte** : dans les niveaux de cours d'eau, les départements sont un fond neutre non touchable ; les cours d'eau du niveau en bleu, les autres en bleu-gris fin. Dans les catégories Départements et Régions, les cours d'eau ne sont pas dessinés.
10. **Pas d'outre-mer** pour l'instant (à discuter : quelques rivières de Guyane ou de La Réunion ?).
11. **Retours du 28 octobre 2026** (essais sur le téléphone) :
    - doublons sur la Seine, l'Oise, la Somme et coupures sur l'Eure : BD TOPAGE contient les bras secondaires, et de petits tronçons de liaison étaient écartés. On ne garde plus que le **cours principal**, d'un seul tenant (longueurs proches des longueurs officielles : Seine 777 km, Eure 227 km) ;
    - la Garonne finissait dans les terres : les contours des départements recouvrent l'**estuaire de la Gironde**. Il est redessiné en eau d'après BD TOPAGE (la Seine et la Loire arrivaient déjà à la mer) ;
    - dans Apprendre, les noms des **départements** sont écrits sous ceux des cours d'eau, dans une autre typographie (cours d'eau en italique bleu, départements en gris, plus petits).

## 4. Fichiers

- `tools/build_rivers.py` → `assets/geo/cours-eau.json` et `assets/geo/cours-eau.geometry.json` (119 Ko).
- Modèle : `FeatureType.RIVER`, champs `departmentIds`, `sandreCodes` ; validation (parent cours d'eau, pas de boucle, départements existants).
- `GeoCatalog` : `tributariesOf`, `mouthOf`, `riversThrough`, `departmentsNamedAfter`.
- Interface : catégorie `GeoCategory.RIVERS`, styles des lignes (`gameStyle`), mode Apprendre (étiquettes des noms, carte cadrée sur la métropole), fiche.
- Tests : `RealRiversTest` (10), 3 tests de validation de plus dans `GeoCatalogLoaderTest`.

## 5. Montagnes (28 octobre 2026)

### Sources

| Besoin | Source | Verdict |
|---|---|---|
| Massifs | **ANCT, périmètres de massifs** (data.gouv.fr « Les périmètres de massifs ») : chaque commune et son massif, d'après le décret du 16 janvier 2004 modifié le 8 septembre 2016, en COG 2021. Licence Ouverte. | **Retenue.** Seule délimitation officielle des massifs. Publiée uniquement en `.xls` (Excel 97-2003) : lue par `tools/xls_reader.py`, petit lecteur en Python standard (pas de nouvelle dépendance). |
| Contours des massifs | Union des contours des **communes 2026** (IGN ADMIN EXPRESS, généralisés par Etalab), même source que les départements | Les contours Etalab sont topologiques (deux communes voisines partagent leurs sommets) : l'union se fait en supprimant les arêtes communes. Passage des communes 2021 aux communes 2026 par le COG 2026 et le fichier des mouvements de communes de l'INSEE (8 communes rétablies depuis 2021). |
| Sommets | **Wikidata** (CC0) : coordonnées et altitude, une révision fixée par sommet | **Retenue.** Domaine public, pas d'attribution obligatoire. Altitude : valeur « préférée » s'il y en a une (mont Blanc : 4 805,59 m, mesure de 2023), sinon la mesure la plus récente. |
| Zones de montagne (urbanisme), zones défavorisées | — | Écartées : ce sont d'autres zonages, plus morcelés ; le massif est la notion attendue dans un quiz. |

### Décisions (à revoir avec moi si besoin)

1. **9 massifs** : les 6 de la loi Montagne (Alpes, Pyrénées, Massif central, Jura, Vosges, Corse) et les 3 d'outre-mer (Guadeloupe, Martinique, Hauts de la Réunion). Attention : le périmètre légal d'un massif comprend aussi ses piémonts, d'où un Massif central très étendu (jusqu'à l'Yonne et à la Côte-d'Or). Une commune classée « partiellement » dans un massif est comptée dedans.
2. **30 sommets**, rattachés à leur massif : les points culminants (mont Blanc, Vignemale, puy de Sancy, crêt de la Neige, Grand Ballon, monte Cinto) et des sommets connus (Barre des Écrins, Meije, mont Ventoux, puy de Dôme, mont Gerbier de Jonc, Canigou, Donon…), plus les volcans d'outre-mer (Soufrière, montagne Pelée, piton des Neiges, piton de la Fournaise).
3. **Une catégorie « Montagnes »** avec deux types de lieux : `massif` (zone) et `peak` (point, triangle sur la carte). Questions : le nom seulement. Niveaux : massifs (métropole, ou tous), sommets par massif, tous les sommets. Nouveaux critères dans `levels.json` : `types`, `parentIds`.
4. **Fiches** : massif (ses sommets avec leur altitude, les départements où il a au moins une commune) ; sommet (altitude, massif, département où il se trouve). La fiche d'un département liste ses massifs.
5. **Carte** : dans un niveau de sommets, la carte est cadrée sur le massif, montré en fond (non touchable) ; les autres sommets sont grisés. Dans Apprendre, noms des massifs en capitales espacées brunes, sommets avec leur altitude.
6. **Dans les catégories Départements, Régions et Cours d'eau**, massifs et sommets ne sont pas dessinés.

### Fichiers

- `tools/build_mountains.py` → `assets/geo/montagnes.json` et `montagnes.geometry.json` (93 Ko) ; `tools/xls_reader.py`.
- `tools/build_geo.py` : le calcul des cartouches d'outre-mer est sorti dans `build_frames()` pour placer aussi massifs et sommets d'outre-mer (la carte produite est identique, vérifié octet par octet).
- Modèle : `FeatureType.MASSIF` et `PEAK`, `Category.MOUNTAIN`, champs `altitudeM` et `wikidata` ; validation de l'altitude.
- Tests : `RealMountainsTest` (7), 1 test de validation de plus dans `GeoCatalogLoaderTest`.
