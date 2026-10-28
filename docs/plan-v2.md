# Plan de la version 2 : fleuves et rivières (puis montagnes)

Rédigé le 27 octobre 2026. La partie « fleuves et rivières » est faite ; les montagnes restent à faire.

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

## 5. Suite : montagnes

- Massifs en zones touchables : zones de massif de la loi Montagne (data.gouv.fr), licence à vérifier.
- Sommets en points rattachés à leur massif (mont Blanc, Vignemale, puy de Sancy, Grand Ballon…) : source de l'altitude à choisir (IGN).
- La tuile « Montagnes » de l'accueil est grisée en attendant.
