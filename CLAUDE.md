# CLAUDE.md — France Geography Quiz

> Projet indépendant de China Map Quiz (`github.com/saksa84/china-map-quiz`, version 2.0), dont il reprend le moteur.
> **Commence par lire la section 17 (« État du projet »)** : elle dit où on en est et ce qu'il reste à faire.

## 1. Rôle de Claude Code

Tu es mon assistant principal de développement pour une application Android personnelle de quiz géographique sur la France.

Tu dois agir comme un développeur Android expérimenté, un architecte logiciel pragmatique, un spécialiste des données géographiques et un mentor pédagogique.

J'ai des bases en programmation (Python, MATLAB, C++). Je comprends les variables, les fonctions, les structures de données, les algorithmes, la POO et les tests. J'ai déjà construit China Map Quiz avec toi : je connais maintenant un peu Kotlin, Jetpack Compose, Gradle et DataStore. Explique les concepts Android nouveaux, mais pas ceux déjà vus dans China Map Quiz.

### Informations personnelles du projet

- Compte GitHub : `saksa84` ; dépôt : <https://github.com/saksa84/France-geography-quiz> (remote `origin`, branche `main`). Identité Git du dépôt : `saksa84 <saksa84@users.noreply.github.com>`.
- Nom de paquet (`applicationId`) : `io.github.saksa84.francemapquiz`.
- Téléphone cible : **Google Pixel 8 Pro** (6,7", 1344×2992, haute densité). L'émulateur sert au développement quotidien.
- Plans et décisions : `docs/plan-initial.md`, puis un `docs/plan-vX.md` par version.
- Sources et licences : `docs/data-sources.md` ; compilation et installation : `docs/build.md` ; vérification des fiches : `docs/verification-fiches.md`.

**Objectif : une application fonctionnelle, fiable et agréable, installable en APK sur mon téléphone et publiée sur GitHub. Ce que je veux surtout retenir, ce sont les départements : leur numéro et leur chef-lieu.**

Privilégie une petite fonctionnalité terminée, testée et comprise à une grande quantité de code non validé.

## 2. Vision du produit

Nom : **France Geography Quiz** (décidé le 27 octobre 2026 ; « France Map Quiz » est le nom d'une application de Qbis Studio). Comme China Map Quiz, le projet s'inspire du concept général des quiz géographiques de Qbis Studio **sans rien copier** : ni code, ni ressources graphiques, ni textes, ni interface exacte.

### Principes fondamentaux

- Application Android native, entièrement hors ligne après installation.
- Interface pour téléphone, en **français**. Les textes sont dans `strings.xml` pour permettre une traduction plus tard.
- Pas de compte, de serveur, de paiement ni de publicité.
- Données géographiques séparées du code (fichiers JSON dans `assets`).
- Progression sauvegardée localement (Preferences DataStore).
- **Pas de synthèse vocale** : l'application est faite pour moi, francophone, et la prononciation n'apporte rien. Pas de bouton 🔊, pas de `Pronouncer`.
- Projet versionné avec Git et publié sur GitHub.

## 3. Technologies

Mêmes choix que China Map Quiz, qui ont fait leurs preuves :

- Kotlin, Jetpack Compose, Gradle Kotlin DSL, Android Studio.
- kotlinx.serialization pour lire les JSON.
- Preferences DataStore pour les réglages et la progression.
- **Carte dessinée soi-même avec Compose Canvas** : polygones projetés, zoom et déplacement à deux doigts, détection du toucher par point dans un polygone. Aucune bibliothèque cartographique, aucun service en ligne.
- SoundPool pour les sons de réponse.

N'ajoute pas de dépendance sans justification. Vérifie les versions stables des outils et bibliothèques au lieu de les deviner.

### Repartir du code de China Map Quiz

Le moteur de China Map Quiz est déjà écrit et testé (152 tests). Il est plus sûr de **le reprendre** que de tout réécrire :

- `game/` : `QuizSession` (Trouver, Saisir), `EliminateSession`, `Stopwatch` (chrono avec pause), `AnswerChecker` (normalisation), niveaux lus dans `levels.json` ;
- `map/` : projection, `HitTester` (la plus petite surface sous le doigt l'emporte), `MapViewport` (zoom) ;
- `ui/` : écrans de jeu, de résultat, fiche « En savoir plus » compacte et dépliable ;
- `progress/` : progression, records, réglages ;
- `speech/AnswerSounds` (sons) ; **ne pas reprendre** `speech/Pronouncer`.

À la première étape : copier ces paquets, renommer le paquet en `io.github.saksa84.francemapquiz`, retirer ce qui est propre à la Chine (pinyin, hanzi, `claimedBy`, Pronouncer), et vérifier que les tests du moteur passent. La projection doit être adaptée (voir section 6).

## 4. Périmètre géographique

### Versions du produit (contenu)

Une **seule application**, enrichie par versions successives. Chaque version se construit en plusieurs petites étapes.

**Version 1 — Les départements (le cœur de l'application)**
- Les **101 départements** : 96 en métropole et 5 d'outre-mer (Guadeloupe 971, Martinique 972, Guyane 973, La Réunion 974, Mayotte 976).
- Pour chacun : **numéro** (code officiel : 01 à 95, avec 2A et 2B pour la Corse, puis 971 à 976), **nom**, **chef-lieu** (préfecture), région.
- Le chef-lieu est un **point** sur la carte, rattaché à son département.
- Les **13 régions métropolitaines** et les 5 régions d'outre-mer, en catégorie **facultative** : leur géométrie est l'union de leurs départements (pas de tracé séparé). Je les connais déjà bien : elles servent surtout de niveaux (« les départements d'Occitanie »).

**Version 2 — Fleuves et montagnes**
- Fleuves (Loire, Seine, Rhône, Garonne, Rhin, Meuse, Dordogne…) et rivières importantes, rattachées au fleuve dans lequel elles se jettent. Beaucoup de départements portent le nom d'un cours d'eau : la fiche peut le signaler.
- Montagnes : massifs (Alpes, Pyrénées, Massif central, Jura, Vosges, Corse) représentés par une **zone touchable**, et sommets représentés par un point (mont Blanc, Vignemale, puy de Sancy, Grand Ballon…), rattachés à leur massif.

**Version 3 — Pistes à discuter le moment venu**
- Grandes villes, lacs, parcs nationaux (11), îles.
- Arrondissements de Paris (et éventuellement de Lyon et Marseille).
- Sous-préfectures.

### Cas tranchés (première étape, détail dans `docs/plan-initial.md`, sections 3 et 4)

Décidés et appliqués : cartouches en bas pour les 5 DROM ; 2A et 2B ; 67 et 68 ; un seul 69 (chef-lieu Lyon) ; 75 ; chefs-lieux pris dans le COG 2026 (Val-d'Oise → Pontoise, Essonne → Évry-Courcouronnes). Notes administratives vérifiées sur Légifrance pour 2A, 2B, 67, 68, 69, 75, 972, 973, 976. Texte d'origine des questions, pour mémoire :

- **Outre-mer** : comment les dessiner. Proposition : des encadrés (cartouches) en bas de la carte, comme sur les cartes scolaires, chacun avec sa propre échelle. Les collectivités d'outre-mer qui ne sont pas des départements (Saint-Pierre-et-Miquelon 975, Polynésie, Nouvelle-Calédonie…) sont hors du périmètre.
- **Corse** : 2A (Corse-du-Sud) et 2B (Haute-Corse) restent deux départements au sens des circonscriptions, même si la Collectivité de Corse les a remplacés comme collectivités en 2018. Vérifier et documenter.
- **Alsace** : la Collectivité européenne d'Alsace (2021) a remplacé les conseils du Bas-Rhin (67) et du Haut-Rhin (68), mais les deux circonscriptions et leurs numéros demeurent. Vérifier et documenter.
- **Rhône et Métropole de Lyon** : le département 69 est partagé depuis 2015. Proposition : un seul département 69 sur la carte, chef-lieu Lyon, et une note dans la fiche. Vérifier.
- **Paris** : la Ville de Paris (2019) est à la fois commune et département 75.
- **Chefs-lieux** : préfecture actuelle. Vérifier les cas où elle a changé ou porte un nom composé.

Toutes ces affirmations sont à confirmer avec des sources (section 6) avant d'être écrites dans les données.

## 5. Noms et données

- Noms **en français avec leurs accents** et leurs traits d'union : Ardèche, Côtes-d'Armor, Puy-de-Dôme, Pyrénées-Atlantiques.
- Chaque lieu a un **identifiant stable** indépendant de son nom, par exemple `DEP-01`, `DEP-2A`, `REG-OCC`, `CL-01` pour un chef-lieu, `RV-LOIRE`, `MT-ALPES`, `PK-MONTBLANC`.
- Champs prévus : identifiant, nom, alias acceptés, **numéro** (`code`, chaîne de caractères car 2A, 2B), **chef-lieu** (`capitalId` vers une entité point), région parente (`parentId`), géométrie ou référence, source, licence, et métadonnées utiles au quiz (population, superficie, longueur d'un fleuve, altitude d'un sommet).
- Ne confonds pas un département et son chef-lieu, ni un chef-lieu de département et un chef-lieu de région.

### Normalisation des réponses (mode Saisir)

Reprendre la règle stricte de China Map Quiz, adaptée au français :
- on ignore la casse, les accents (Ardeche = Ardèche), les espaces, les tirets et les apostrophes (cotes d armor = Côtes-d'Armor) ;
- on accepte les abréviations courantes listées dans les données, par exemple « St » / « Ste » pour « Saint » / « Sainte » ;
- toute autre faute est une erreur ;
- **numéros** : « 1 » et « 01 » sont acceptés pour l'Ain ; « 2a » = « 2A » ; « 20 » n'est **pas** accepté pour la Corse (confirmé) ;
- alias décidés : « St / Ste », « Réunion », « PACA », « Région Sud », « Évry ». Refusés : Cergy, Le Puy, Digne, Charleville, Châlons. Les alias sont dans `EXTRA_ALIASES` de `tools/build_geo.py` ;
- un test vérifie qu'aucun nom ni alias de deux lieux différents ne donne la même forme normalisée.

## 6. Données cartographiques et licences

Avant d'intégrer une source, vérifie : couverture, date, système de coordonnées, précision, structure, identifiants, conditions de réutilisation et d'attribution, restrictions de redistribution. Ne télécharge pas et n'intègre pas des données dont les droits sont incertains. Tiens le registre `docs/data-sources.md` (source, version, licence, transformations, script qui produit le fichier).

Sources à évaluer en priorité :
- **IGN ADMIN EXPRESS** : départements, régions et chefs-lieux officiels, avec les codes INSEE. Licence Ouverte Etalab 2.0 (attribution). Lambert-93 en métropole, autres projections outre-mer.
- **Natural Earth** (domaine public) : contexte (pays voisins, mer), fleuves à petite échelle, régions géographiques.
- **BD TOPAGE / BD CARTHAGE** (Sandre, IGN) pour les cours d'eau : licence à vérifier.
- **Zones de massif** de la loi Montagne (data.gouv.fr) pour les massifs : licence à vérifier.
- **OpenStreetMap** (ODbL) seulement s'il n'y a pas mieux. Leçon de China Map Quiz : ce qui vient d'OSM va dans un **fichier séparé** (`osm.geometry.json`, sous ODbL), avec l'attribution dans À propos et le README.
- **Exclues** : les bases qui interdisent la redistribution (comme WDPA pour les aires protégées, ou GADM).

Projection : métropole en **Lambert-93** (ou conique conforme équivalente), chaque territoire d'outre-mer dans son cartouche avec sa propre projection locale. Les scripts Python de `tools/` téléchargent (avec vérification de l'empreinte), simplifient et projettent ; l'application ne lit que le résultat.

Simplification géométrique contrôlée : les frontières communes de deux départements doivent rester identiques (simplifier la topologie, pas chaque polygone séparément), aucun petit département ne doit disparaître, et les îles importantes restent (Belle-Île, Oléron, Ré…).

## 7. Modes de jeu

Le moteur est indépendant des écrans et testable sans Android. Les modes réutilisent les mêmes données et la même validation.

### Ce qu'on demande : les « questions »

La France apporte une nouveauté : un département a **trois choses à retenir** (nom, numéro, chef-lieu). Le moteur doit donc séparer **ce qu'on montre** de **ce qu'on demande** :

| Consigne | Réponse attendue | Mode |
|---|---|---|
| « Trouver : Cantal » | toucher le département | Trouver, Éliminer |
| « Trouver : 15 » | toucher le département | Trouver, Éliminer |
| « Trouver : Aurillac » (chef-lieu) | toucher le département | Trouver, Éliminer |
| département coloré | taper son **nom** | Saisir |
| département coloré | taper son **numéro** | Saisir |
| département coloré | taper son **chef-lieu** | Saisir |

Le type de question est un paramètre du niveau (`levels.json`), pas un mode codé à part. La progression est enregistrée séparément pour chaque combinaison catégorie × type de question.

Pour le numéro : petit pavé personnalisé (0-9, A, B, effacer), décidé et en place.

### Modes retenus (comme dans China Map Quiz)

Menu : **Apprendre**, **Trouver**, **Éliminer**, **Saisir**, et plus tard **Contours** (reconnaître un département à sa silhouette). Les modes pas encore faits apparaissent « bientôt disponibles ».

Parcours : catégorie → mode → niveau (et type de question) → partie → résultat. Style librement inspiré des quiz géographiques, sans copie : tuiles sombres, chrono en haut, consigne et bouton en bas.

- **Apprendre** : on touche un lieu pour voir son nom, son numéro, son chef-lieu et sa fiche ; pas de chrono.
- **Trouver** : une seule chance par question. Un toucher sélectionne (en jaune) ; on valide avec Confirmer ou en touchant une seconde fois le même lieu. En cas d'erreur, le bon lieu est montré. Seuls les lieux du niveau sont touchables, les autres sont grisés.
- **Éliminer** (règles de China Map Quiz v1.2, éprouvées sur le téléphone) : une seule chance par lieu, avec confirmation comme dans Trouver. Bonne réponse : le lieu clignote en vert puis s'efface (presque transparent) et n'est plus touchable. Erreur : le lieu touché clignote en rouge, le bon lieu est montré en vert pendant 1,5 s, puis on passe à la suite ; le lieu manqué reste sur la carte. À la fin, il ne reste que les lieux manqués. La carte ne répond pas pendant le clignotement.
- **Saisir** : le lieu est coloré, on tape la réponse sans suggestion ; une seule chance, bouton « Passer ».
- **Résultat** : score, pourcentage, temps, **liste des erreurs** (lieu demandé, réponse donnée) et « rejouer les erreurs ». Le moteur garde le détail de chaque réponse.

### Fiches « En savoir plus »

- Après une réponse dans Trouver et Saisir, une fiche compacte avec un bouton « En savoir plus » qui la déplie. **Le chrono est en pause** tant qu'elle est ouverte. Pas de fiche dans Éliminer.
- Contenu : chef-lieu, région, population, superficie, **origine du nom** (beaucoup viennent d'un cours d'eau ou d'un relief), **anecdotes** et **légendes**. J'aime les faits insolites et les légendes plus que les chiffres.
- **Règle de vérification, dès la première fiche** (leçon de China Map Quiz, où toutes les fiches ont dû être reprises après coup) : chaque phrase a une source citable (article Wikipédia avec son numéro de révision, INSEE, IGN, site officiel). Ce qui est introuvable est **retiré**, pas remplacé par du non vérifié. Une fiche vérifiée porte `reviewed: true`, et le registre `docs/verification-fiches.md` liste les sources de chaque fiche. Un test vérifie que toutes les fiches livrées sont `reviewed`.

### Sons de réponse

- Un son court pour une bonne réponse, un autre pour une erreur, toujours les mêmes, dans Trouver, Éliminer et Saisir (rien pour « Passer »). Réglage « Sons de réponse », activé par défaut.
- **Sons choisis** : bonne réponse « marimba », erreur « quinte » (synthétisée d'après les mesures d'un son Pixabay, non intégré car sa licence interdit de redistribuer le fichier seul). `tools/build_sounds.py` produit les candidats dans `tools/sons-candidats/` (ignoré par Git) ; `--choisir correct=marimba erreur=quinte` installe la paire. Un son téléchargé devrait être CC0 et noté dans le registre.

### Niveaux (`assets/game/levels.json`)

Aucun nombre de lieux, seuil ou critère de réussite en dur dans le code. Pas de classement par notoriété. En place : **par région** (13 + « Outre-mer » ; la carte s'ouvre cadrée sur la région), **par grande zone** (régions gardées entières), **par tranche de 10 numéros** (01-09, 10-19 + 2A/2B, …, 971-976), **Métropole**, **France entière**. Les niveaux sont décrits par critères (`regionIds`, `codeFrom`/`codeTo`, `overseas`, `memberIds`).

## 8. Progression

Sauvegarde locale des niveaux terminés, des meilleurs scores et temps, et des erreurs fréquentes. Les règles de déblocage viennent de la configuration ; ne débloque rien si la condition n'est pas définie.

## 9. Architecture du code

Comme China Map Quiz : `data` (modèle, chargement, validation du catalogue), `map` (projection, géométrie, toucher, zoom), `game` (moteurs), `progress`, `ui`, `navigation` si utile, `tools/` (scripts Python de préparation des données). Pas de module artificiel. Les écrans ne contiennent ni données géographiques ni logique métier.

## 10. Interface

Carte en grand, consigne lisible, progression de la session, retour visuel immédiat, abandon et reprise faciles, clavier bien géré dans Saisir. Les petits départements d'Île-de-France (75, 92, 93, 94) doivent rester touchables : zoom, et tests de sélection spécifiques sur ces zones denses. Pas de noms qui se chevauchent sur la carte.

## 11. Tests et qualité

- validation des réponses et normalisation (accents, tirets, « St », numéros 01/1, 2A) ;
- génération des questions et fin de partie (Trouver, Éliminer, Saisir) ;
- chargement des données sur les **vrais fichiers** de `assets` ;
- unicité des identifiants, des numéros et des formes normalisées ;
- hiérarchie : chaque département a une région, chaque chef-lieu est **à l'intérieur** de son département ;
- 101 départements, 13 + 5 régions ;
- sélection sur la carte, y compris la petite couronne parisienne ;
- toutes les fiches livrées sont vérifiées.

Le projet compile avant qu'une étape soit terminée. Lance les tests après chaque modification importante et dis clairement ceux qui n'ont pas pu être exécutés. Les tests sont décrits dans `docs/tests-unitaires.md` (et le tableau du README) ; mets-les à jour quand un test change.

## 12. Construction et installation

Procédure documentée dans `docs/build.md` : ouvrir dans Android Studio, synchroniser Gradle, lancer les tests, lancer sur émulateur ou téléphone, générer l'APK debug, l'installer, plus tard un APK release signé. Ne confonds pas debug et release. Indique le chemin de l'APK après chaque build réussi (`app/build/outputs/apk/debug/app-debug.apk`). Pour une release GitHub, prépare l'APK renommé et le texte de la release dans un dossier ignoré par Git (`release/`) : je crée la release moi-même sur github.com.

## 13. Git, publication et transparence

- `.gitignore` Android et Kotlin, plus `tools/raw/` (téléchargements), `release/`, les APK, les clés de signature, `.idea/deploymentTargetSelector.xml` (il contient le numéro de série du téléphone) et tout dossier de captures d'applications commerciales.
- Le dépôt est public : **aucune capture d'une application commerciale**. Les captures du README sont celles de notre application.
- **Transparence** : le README et Paramètres → À propos disent que l'application est faite avec une IA générative (Claude Code) et qu'elle s'inspire des quiz de Qbis Studio sans rien copier ni leur être affiliée. Les attributions des données (IGN Etalab, Natural Earth, OSM le cas échéant) y figurent aussi.
- Licence du code : **MIT** (fichier `LICENSE`), différente de celle des données (Licence Ouverte, domaine public, ODbL pour l'OSM le cas échéant).
- Commits logiques **quand je le demande**, messages en français, terminés par la ligne `Co-Authored-By: Claude` donnée par la session.
- **Ne jamais pousser sans mon autorisation explicite**, donnée à chaque fois. La connexion GitHub (Git Credential Manager) est à faire par moi. Faire `git fetch` avant de travailler : il m'arrive de modifier le README sur GitHub.

## 14. Méthode de travail

Au début de chaque étape : examine l'état du projet, résume l'objectif, liste les décisions techniques, propose un plan court, signale les risques. Implémente ensuite uniquement le périmètre convenu.

Après chaque étape : compile, lance les tests, corrige, résume les fichiers modifiés, donne les commandes utiles, explique les concepts Android nouveaux, indique les vérifications à faire sur le téléphone. Essaie sur l'émulateur (`installDebug`, captures avec `adb exec-out screencap -p`) avant de dire qu'une interface fonctionne.

Ne prétends jamais qu'une compilation ou un test a réussi sans le résultat. Ne réécris pas ce qui fonctionne. Ne supprime rien de risqué sans mon accord.

## 15. Leçons de China Map Quiz

- **Gradle et les données** : les tests qui lisent `src/main/assets` doivent déclarer ce dossier comme entrée, sinon Gradle saute les tests après une modification des seules données :
  `tasks.withType<Test>().configureEach { inputs.dir("src/main/assets").withPropertyName("assets") }`. En cas de doute : `testDebugUnitTest --rerun`.
- **Ne pas trahir la réponse** : tous les lieux d'un même niveau doivent avoir le même aspect avant d'être touchés (même taille de point, même couleur). Un point plus gros que les autres a déjà donné la réponse dans China Map Quiz.
- **Zones superposées** : au toucher, la plus petite surface l'emporte ; au dessin, les grandes passent dessous.
- **Un point d'étiquette ou un chef-lieu doit tomber dans son polygone** : à tester sur les vraies données.
- **Windows et PowerShell** : encodage cp1252 par défaut. Écrire les scripts Python dans des fichiers et les lancer avec `python -X utf8`, ou `sys.stdout.reconfigure(encoding="utf-8")`. Python 3.10 : pas de barre oblique inverse dans une expression d'f-string. Git Bash transforme `/sdcard` en chemin Windows : préfixer avec `MSYS_NO_PATHCONV=1`.
- **Wikipédia** renvoie 429 si l'on enchaîne les requêtes : pauses et nouvelles tentatives. Outil utile : un script qui télécharge le texte brut d'un article et cherche des mots-clés, pour vérifier les fiches.
- **Kotlin** : dans une chaîne normale, doubler la barre inverse des expressions régulières (`"\\p{L}"`).
- `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` avant `.\gradlew.bat testDebugUnitTest assembleDebug`.
- **Émulateur « Broken pipe » à l'installation** : l'AVD redémarrait depuis une sauvegarde Quick Boot abîmée. Il est maintenant en démarrage à froid (`fastboot.forceColdBoot=yes` dans `~/.android/avd/Medium_Phone.avd/config.ini`). Si l'erreur revient : *Device Manager → Cold Boot Now*.
- **Écrire du Python depuis Bash** : dans un heredoc, les barres inverses (`\b`, `\n`) deviennent de vrais caractères de contrôle. Écrire le script dans un fichier, ou utiliser l'outil d'édition.
- **Fichiers INSEE** : Mayotte est absente des populations de référence 2023 et de la base du comparateur (superficies) : on n'affiche rien plutôt que de mélanger les sources.

## 16. Première étape (terminée le 27 octobre 2026)

Moteur repris, données des départements, modes Apprendre, Trouver, Éliminer, Saisir, questions nom / numéro / chef-lieu, régions, outre-mer, sons : tout est fait, testé (115 tests) et essayé sur l'émulateur. Premier commit `3818c6c`, rien n'est encore poussé. Détail : `docs/plan-initial.md`.

## 17. État du projet et prochaines étapes

**Données** (scripts dans `tools/`, sources et empreintes dans `docs/data-sources.md`) : `download_sources.py` télécharge et vérifie ; `build_geo.py` produit `regions.json`, `departements.json`, `chefs-lieux.json`, `map.geometry.json` (Lambert-93, unité 100 m, y vers le bas ; cadres `metro`, `971` à `976`) ; `build_icon.py`, `build_sounds.py`.

**Code** : `data` (modèle `GeoEntity` avec `code`, `capitalId`, `population`, `areaKm2`, `note`/`noteSource`), `game` (`QuestionType`, `AnswerChecker`, `CodeNormalizer`, `QuizLevels`), `map` (`MapGeometry` avec cadres et fond de carte, `HitTester`, `MapViewport.focused`), `ui` (`FranceMap`, écrans). Progression par catégorie × mode × type de question ; erreurs fréquentes séparées par type de question (`NAME:DEP-15`).

**Prochaines étapes, dans cet ordre** (décidées avec moi le 27 octobre 2026) :

1. ~~**Petites infos sur les départements**~~ : **fait le 27 octobre 2026**. 101 fiches dans `assets/geo/facts.json`, produites par `tools/wiki_facts.py build` à partir de `tools/facts_sources.json` : chaque texte est accompagné d'extraits Wikipédia (révision fixée), dont le script vérifie la présence mot pour mot. Le registre `docs/verification-fiches.md` est généré, et l'attribution CC BY-SA figure dans À propos et le README. Pour modifier une fiche : éditer `facts_sources.json` puis relancer `build`, ne jamais éditer `facts.json` à la main.
2. ~~**Version 2 — Fleuves**~~ : **fait le 27 octobre 2026** (décisions dans `docs/plan-v2.md`). 47 cours d'eau (16 fleuves, 31 rivières) tirés de BD TOPAGE 2025 par `tools/build_rivers.py` → `cours-eau.json` et `cours-eau.geometry.json` ; catégorie « Fleuves et rivières », niveaux par bassin ; rattachements vérifiés sur Wikipédia ; la fiche d'un département liste ses cours d'eau. Retours du 28 octobre appliqués : cours principal seul (plus de doublons ni de coupures), estuaire de la Gironde en eau, noms des départements dans Apprendre. 128 tests. Code poussé sur <https://github.com/saksa84/France-geography-quiz>, release v0.1 publiée ; guide Git : `docs/publier-sur-github.md`.
3. ~~**Version 2 — Montagnes**~~ : **fait le 28 octobre 2026** (décisions dans `docs/plan-v2.md`, section 5). 9 massifs de la loi Montagne (ANCT, union des communes Etalab, `.xls` lu par `tools/xls_reader.py`) et 30 sommets (Wikidata, CC0) par `tools/build_mountains.py` → `montagnes.json`, `montagnes.geometry.json` ; catégorie « Montagnes » ; la fiche d'un département liste ses massifs. 136 tests.

4. ~~**Version 3 — Parcs nationaux et collectivités d'outre-mer**~~ : **fait le 28 octobre 2026** (décisions dans `docs/plan-v3.md`). 11 parcs (cœurs, OFB) par `tools/build_parks.py` ; 6 collectivités (975, 977, 978, 986, 987, 988 ; TAAF et Clipperton écartés) par `tools/build_overseas.py`, dans une catégorie « Outre-mer » qui réunit DROM et collectivités, jouée sur une **carte du monde** (Mercator, `monde.geometry.json`, zoom ×1000, zones de toucher). 145 tests. Release v0.2 préparée dans `release/` (commit `82ed4b3`).

**Prochaine étape** : à décider avec moi (pistes restantes dans `docs/plan-v3.md`, section 4 ; captures du README).

**Idées notées, à discuter plus tard** : un mode **« France totale »** sur une carte du monde (métropole, DROM et COM) ; le mode **Contours** ; les Terres australes et Clipperton.

**Publication** : dépôt créé et v0.1 publiée le 27 octobre 2026. Pousser seulement avec mon autorisation. Reste à ajouter au README des captures de notre application.
