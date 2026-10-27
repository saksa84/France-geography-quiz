# Plan initial — France Geography Quiz (version 0.1)

État au 25 octobre 2026. Ce document liste ce qui a été fait à la première étape, les décisions prises **avec leur justification**, et les **questions qui restent à trancher** avant la publication sur GitHub (section 4).

## 1. Ce qui est fait

- Projet Android créé en reprenant le moteur de China Map Quiz 2.0 (`game/`, `map/`, `progress/`, `ui/`, sons), paquet renommé en `io.github.saksa84.francemapquiz`.
- Retiré : pinyin, hanzi, `claimedBy`, `Pronouncer` et le bouton 🔊, le réglage « afficher les caractères chinois », la projection d'Albers côté application (les coordonnées arrivent déjà projetées).
- Données : 101 départements, 18 régions, 101 chefs-lieux, contours et cartouches de l'outre-mer, générés par des scripts Python reproductibles (`tools/`).
- Modes jouables : **Apprendre, Trouver, Éliminer, Saisir**, pour les départements (nom, numéro, chef-lieu) et pour les régions (nom, chef-lieu). « Contours » et « Fleuves et montagnes » sont affichés « Bientôt disponible ».
- 111 tests unitaires, tous passants (voir `docs/tests-unitaires.md`).

## 2. Décisions techniques prises

### Sources des données

| Besoin | Source retenue | Pourquoi |
|---|---|---|
| Numéros, noms officiels, régions, **chef-lieu officiel** | INSEE, Code officiel géographique (COG) au 1er janvier 2026 | Référence officielle ; la colonne `CHEFLIEU` donne le code commune du chef-lieu, ce qui évite toute saisie à la main. |
| Contours des départements | IGN ADMIN EXPRESS COG 2026, **généralisé à 100 m par Etalab** | Même source que ADMIN EXPRESS, même licence, mais déjà en GeoJSON WGS84 avec la **topologie conservée** (frontières communes identiques) : 0,8 Mo au lieu de plusieurs centaines. |
| Position des chefs-lieux | Etalab, `mairies.geojson` 2026 (dérivé d'ADMIN EXPRESS) | Le point est la mairie de la commune chef-lieu : il est forcément dans son département. |
| Pays voisins (fond de carte) | Natural Earth 5.1.2 | Domaine public ; seulement décoratif. |

Licence Ouverte Etalab 2.0 pour l'INSEE et l'IGN : attribution obligatoire, faite dans le README et dans Paramètres → À propos. Détail, empreintes SHA-256 et transformations : `docs/data-sources.md`. Rien ne vient d'OpenStreetMap.

### Carte

- **Métropole en Lambert-93** (EPSG:2154, ellipsoïde GRS80, formules officielles de l'IGN), unité de carte = 100 m.
- **Outre-mer dans 5 cartouches** carrés, en rangée sous la métropole, de gauche à droite : Guadeloupe, Martinique, Guyane, La Réunion, Mayotte. Chacun a sa projection locale et **sa propre échelle** (Guadeloupe ×2,2, Martinique ×3,5, Guyane ×0,47, La Réunion ×2,9, Mayotte ×4,7 par rapport à la métropole). Un cadre fin entoure chaque cartouche ; aucun nom n'y est écrit en jeu (ce serait donner la réponse).
- Simplification par frontière partagée (algorithme de China Map Quiz) avec une tolérance de 250 m : 125 000 → 39 000 sommets, fichier de 495 Ko. Les îles de plus de 1 km² sont gardées (Belle-Île, Ré, Oléron, Yeu, Groix, Ouessant, Marie-Galante, les Saintes, Petite-Terre…).
- **Chefs-lieux côtiers** : après simplification du littoral, 8 mairies tombaient en mer ou trop près du bord ; elles sont déplacées vers le point intérieur le plus proche (Nice, Marseille, Ajaccio, Bastia, Mâcon, Toulon, Basse-Terre : 0,1 à 0,2 km ; Cayenne : 0,9 km). Le script l'affiche à chaque exécution.
- Pays voisins dessinés uniquement dans le cadre de la métropole, sous les départements, d'une couleur discrète.

### Jeu

- Le **type de question** (nom, numéro, chef-lieu) est choisi sur l'écran des niveaux, par des boutons « On demande : Nom / Numéro / Chef-lieu ». La progression est enregistrée **par catégorie × mode × type de question**, et les erreurs fréquentes aussi (manquer le numéro du Cantal n'est pas manquer son nom).
- Dans Trouver et Éliminer, la consigne est « Trouver : Cantal », « Trouver : 15 » ou « Trouver : Aurillac », avec en dessous une petite mention « numéro du département » ou « chef-lieu du département » pour lever l'ambiguïté.
- **Saisir un numéro** : petit pavé personnalisé (0-9, A, B, effacer), qui ne cache pas la carte, au lieu du clavier du téléphone.
- Les **chefs-lieux ne sont pas dessinés en jeu** (Trouver, Éliminer, Saisir) : ils n'apparaissent qu'en mode Apprendre. Leur point ne donnerait pas le nom, mais il attirerait l'œil sur la question « chef-lieu ».
- **Régions** : catégorie facultative, gardée parce qu'elle ne coûte presque rien (une région = l'union de ses départements, toucher un département choisit sa région). Questions : nom et chef-lieu de région (pas de numéro).
- **Mode Apprendre** : vue départements ou régions ; étiquettes au choix « Noms », « Numéros » ou « Chefs-lieux » (les numéros sont pratiques pour réviser).
- **Fiches** : la structure est prête (origine du nom, histoire, légende, anecdotes, `reviewed`), mais **aucune fiche n'est livrée** : la règle impose de vérifier chaque phrase avec une source avant de l'écrire. La fiche compacte en jeu montre déjà nom, numéro, chef-lieu, région et superficie, qui viennent des données sourcées.

### Niveaux (`assets/game/levels.json`)

Proposition de CLAUDE.md appliquée, à valider (voir questions 6 et 7) :

- **Par région** : les 13 régions de métropole, plus « Outre-mer » (5 départements).
- **Par grande zone**, en gardant les régions entières : Nord-Ouest (Bretagne, Normandie, Pays de la Loire, Centre-Val de Loire, Île-de-France : 28), Nord-Est (Hauts-de-France, Grand Est, Bourgogne-Franche-Comté : 23), Sud-Ouest (Nouvelle-Aquitaine, Occitanie : 25), Sud-Est (Auvergne-Rhône-Alpes, PACA, Corse : 20).
- **Par numéro** : 01 à 2B (21), 21 à 40, 41 à 60, 61 à 80, 81 à 95 (15), 971 à 976 (5). 2A et 2B sont rangés à la place de l'ancien 20.
- **Toute la France** : Métropole (96), France entière (101).
- Régions : Métropole (13), France entière (18).

Aucun nombre n'est écrit dans le code : les niveaux sont décrits par des critères (régions, tranche de numéros, outre-mer oui/non) et le test `QuizLevelsTest` vérifie que chaque découpage couvre chaque département une et une seule fois.

## 3. Cas de la section 4 de CLAUDE.md

Vérifiés sur le **Code officiel géographique 2026** de l'INSEE (fichiers `v_departement_2026.csv` et `v_region_2026.csv`), qui fait foi pour les numéros et les chefs-lieux. Les dates et les lois sont indiquées pour la note de fiche proposée ; elles sont **à confirmer sur Légifrance** avant d'écrire la moindre note dans les données (aucune n'y est pour l'instant).

| Cas | Ce que dit le COG 2026 | Décision dans l'application | Note de fiche proposée (non livrée) |
|---|---|---|---|
| **Outre-mer** | 5 départements : 971, 972, 973, 974, 976 (régions 01 à 06, sans 05) | 5 cartouches en bas de la carte. Les collectivités présentes dans le fichier des contours (975 Saint-Pierre-et-Miquelon, 977, 978, 984, 986, 987, 988, 989) sont **écartées**. | Guyane et Martinique : collectivités territoriales uniques depuis 2015 (loi n° 2011-884 du 27 juillet 2011). Mayotte : département depuis 2011. |
| **Corse** | 2A Corse-du-Sud (chef-lieu Ajaccio) et 2B Haute-Corse (chef-lieu Bastia), région 94 | Deux départements, numéros 2A et 2B. « 20 » est **refusé** en mode Saisir. | Depuis le 1er janvier 2018, la Collectivité de Corse remplace les deux conseils départementaux et la collectivité territoriale ; 2A et 2B restent des circonscriptions de l'État (loi NOTRe du 7 août 2015). |
| **Alsace** | 67 Bas-Rhin (Strasbourg) et 68 Haut-Rhin (Colmar), région 44 Grand Est | Deux départements, 67 et 68. | Depuis le 1er janvier 2021, la Collectivité européenne d'Alsace remplace les deux conseils départementaux (loi n° 2019-816 du 2 août 2019). |
| **Rhône et Métropole de Lyon** | Un seul département 69, chef-lieu Lyon ; le fichier des contours n'a qu'un 69 | Un seul 69 sur la carte, chef-lieu Lyon. | Depuis le 1er janvier 2015, la Métropole de Lyon exerce sur son territoire les compétences du département ; la circonscription du Rhône reste unique (loi MAPTAM du 27 janvier 2014). |
| **Paris** | 75, chef-lieu Paris | Département 75, chef-lieu Paris. En mode Saisir, « Paris » est accepté pour les deux questions. | Depuis le 1er janvier 2019, la Ville de Paris est à la fois commune et département (loi n° 2017-257 du 28 février 2017). |
| **Chefs-lieux** | Colonne `CHEFLIEU` | Pris tels quels. Cas notables : **Val-d'Oise → Pontoise** (chef-lieu officiel, alors que la préfecture est à Cergy) ; **Essonne → Évry-Courcouronnes** (commune nouvelle de 2019) ; Var → Toulon ; Guadeloupe → **Basse-Terre** (et non Pointe-à-Pitre) ; Marne → Châlons-en-Champagne ; Mayotte → Mamoudzou ; Haute-Loire → Le Puy-en-Velay. | — |

## 4. Décisions du 27 octobre 2026

Questions tranchées avec moi (saksa84), toutes appliquées le 27 octobre 2026 (115 tests passants).

| # | Question | Décision | État |
|---|---|---|---|
| 1 | Nom de l'application | **France Geography Quiz** | Fait |
| 2 | Licence du code | **MIT** | Fait |
| 3 | « 20 » pour la Corse | **Refusé** : seuls 2A et 2B sont acceptés | Déjà en place |
| 4 | Alias acceptés en mode Saisir | **« Évry »** (Essonne) et **« PACA » / « Région Sud »** (Provence-Alpes-Côte d'Azur). Refusés : Cergy, Le Puy, Digne, Charleville, Châlons | Fait |
| 5 | Sons | Bonne réponse : **marimba**. Erreur : **quinte**, synthétisée d'après les mesures d'un son de Pixabay qui me plaisait (`universfield-wrong-answer-129254.mp3`) : ré puis la, fondamentale et octave seulement, léger écho. Le fichier Pixabay n'est pas intégré, car sa licence interdit de redistribuer le fichier seul ; il reste dans `tools/sons-candidats/`, ignoré par Git. Aucune licence à gérer | Fait (`tools/build_sounds.py --choisir correct=marimba erreur=quinte`) |
| 6 | Grandes zones | **Régions gardées entières** (20 à 28 départements par zone) | Déjà en place |
| 7 | Tranches de numéros | **Par 10** : 01-09, 10-19 + 2A/2B, 21-30, …, 91-95, 971-976 | Fait |
| 8 | Superficie | **Superficie officielle de l'INSEE** au lieu du calcul sur les contours IGN | Fait |
| 9 | Population | **Oui** : populations de référence INSEE 2023 (en vigueur en 2026), avec leur année. **Mayotte n'y figure pas** (ni dans les superficies) : rien n'est affiché pour elle plutôt qu'une autre source | Fait |
| 10 | Notes administratives | **Oui** : 9 départements (2A, 2B, 67, 68, 69, 75, 972, 973, 976), textes vérifiés sur Légifrance, source dans les données (`noteSource`) | Fait |
| 11 | Pavé numérique dans Saisir | **Gardé** | Déjà en place |
| 12 | Chefs-lieux en jeu | **Affichés** dans les questions « chef-lieu » (Trouver, Éliminer), tous identiques | Fait |
| 13 | Fiches « En savoir plus » | **Plus tard** : d'abord finir le jeu et la publication | — |
| — | Niveaux « Par région » | La carte s'ouvre **cadrée sur la région**, le reste de la France grisé autour, avec le dézoom possible | Fait |

### Nouvelles idées, à discuter ensuite

- Un niveau ou une catégorie **outre-mer** qui inclurait aussi les **collectivités d'outre-mer** (Saint-Pierre-et-Miquelon, Saint-Martin, Polynésie, Nouvelle-Calédonie…), pas seulement les 5 départements. Elles sont aujourd'hui hors du périmètre.
- Un mode **« France totale »** : une carte du monde où l'on retrouve tous les départements et territoires, métropole et outre-mer.

## 5. Étapes suivantes proposées

1. Appliquer les décisions de la section 4, puis premier commit et publication du dépôt (par vous, sur github.com).
2. Essai sur le Pixel 8 Pro (APK debug) : taille des cibles en Île-de-France, lisibilité des cartouches, pavé numérique.
3. Fiches vérifiées, par petits lots.
4. Mode Contours.
5. Version 2 : fleuves et montagnes (`docs/plan-v2.md`).
