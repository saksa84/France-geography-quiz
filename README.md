# France Geography Quiz

Application Android personnelle pour apprendre les **départements français** : leur emplacement, leur **numéro** et leur **chef-lieu**. Entièrement hors ligne, sans compte, sans publicité ni collecte de données.

## Fonctionnalités (version 0.2)

- **101 départements** (96 en métropole, 5 d'outre-mer dans des cartouches) et **18 régions**.
- Trois questions possibles pour chaque département : son **nom**, son **numéro** (01 à 95, 2A, 2B, 971 à 976), son **chef-lieu**.
- Modes **Apprendre** (carte à explorer, étiquettes noms / numéros / chefs-lieux), **Trouver**, **Éliminer** et **Saisir** (avec un pavé 0-9 / A / B pour les numéros).
- Niveaux par région (carte cadrée sur la région), par grande zone, par tranche de 10 numéros, métropole ou France entière.
- Résultats détaillés, « rejouer les erreurs », progression et révision des erreurs les plus fréquentes.
- **47 fleuves et rivières** (version 2) : où ils se jettent, leurs affluents, les départements qu'ils traversent ; niveaux par bassin (Loire, Seine, Rhône, Garonne et Dordogne, mer du Nord, fleuves côtiers), fleuves seuls, rivières seules ou tous.
- **Montagnes** (version 2) : les 9 massifs de la loi Montagne (zones) et 30 sommets (triangles) avec leur altitude ; niveaux massifs, sommets par massif, tous les sommets.
- Fiches « En savoir plus » des départements (origine du nom, histoire, légendes, anecdotes), chaque phrase sourcée.
- Carte dessinée par l'application (Jetpack Compose Canvas) : zoom à deux doigts, Île-de-France sélectionnable.

## Construire l'application

Voir [`docs/build.md`](docs/build.md). En bref :

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`.

## Organisation

| Dossier | Contenu |
|---|---|
| `app/src/main/java/.../data` | Modèle (région, département, chef-lieu), chargement et validation |
| `.../game` | Moteurs de jeu (Trouver, Saisir, Éliminer), types de question, niveaux |
| `.../map` | Géométrie, sélection au doigt, zoom |
| `.../progress` | Records, erreurs, réglages (DataStore) |
| `.../ui` | Écrans Jetpack Compose |
| `app/src/main/assets` | Données : `geo/*.json`, `game/levels.json` |
| `tools/` | Scripts Python de préparation des données, de l'icône et des sons |
| `docs/` | Plans, sources, compilation, tests, vérification des fiches, publier sur GitHub (`publier-sur-github.md`) |

## Tests

136 tests unitaires, dont plusieurs sur les vraies données : liste détaillée dans [`docs/tests-unitaires.md`](docs/tests-unitaires.md).

| Domaine | Tests |
|---|---:|
| Données (validation, vrais fichiers, fiches) | 31 |
| Jeu (réponses, niveaux, moteurs, chrono) | 42 |
| Carte (sélection, zoom, vraie carte) | 32 |
| Progression | 10 |

## Données et attributions

- **INSEE**, Code officiel géographique 2026 (numéros, noms, régions, chefs-lieux), populations de référence 2023, base du comparateur de territoires (superficies). Licence Ouverte Etalab 2.0.
- **IGN**, ADMIN EXPRESS COG 2026, contours généralisés par Etalab : contours des départements, position des mairies. Licence Ouverte Etalab 2.0.
- **Natural Earth** : pays voisins. Domaine public (« Made with Natural Earth »).
- **Sandre**, BD TOPAGE® 2025 (OFB, IGN) : tracés des fleuves et rivières. Licence Ouverte Etalab 2.0.
- **ANCT**, Observatoire des territoires : communes classées dans un massif (loi Montagne). Licence Ouverte. Contours des massifs dessinés d'après les communes IGN/Etalab.
- **Wikidata** : coordonnées et altitude des sommets. CC0 (domaine public).
- **Wikipédia** en français : fiches « En savoir plus », reformulées d'après les articles des départements (et de quelques légendes), sous licence CC BY-SA 4.0. Chaque texte est justifié par un extrait d'une révision précise : [`docs/verification-fiches.md`](docs/verification-fiches.md).

Détail des versions, empreintes et transformations : [`docs/data-sources.md`](docs/data-sources.md).

## Transparence

Cette application est réalisée avec une IA générative, **Claude Code** (Anthropic) : code, préparation des données et tests, sous ma direction.

Elle s'inspire du concept général des quiz géographiques de **Qbis Studio** (World Map Quiz, France Map Quiz), **sans rien copier** (ni code, ni ressources graphiques, ni textes) et **sans affiliation** avec Qbis Studio. Elle reprend le moteur de mon projet précédent, [China Map Quiz](https://github.com/saksa84/china-map-quiz).

## Licence

Code sous licence [MIT](LICENSE). Les données restent sous leurs licences d'origine (voir ci-dessus).
