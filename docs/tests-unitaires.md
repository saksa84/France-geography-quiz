# Tests unitaires

115 tests JUnit, exécutés sur le PC sans Android (`.\gradlew.bat testDebugUnitTest`). Les tests marqués **(vraies données)** lisent les fichiers livrés dans `app/src/main/assets` : Gradle les relance dès que ces fichiers changent (déclaration dans `app/build.gradle.kts`).

| Classe | Tests | Ce qui est vérifié |
|---|---:|---|
| `data/GeoCatalogLoaderTest` | 13 | Validation des données sur de petits exemples : numéros officiels (20, 96, 975, « 3 » refusés ; 2A accepté), outre-mer cohérent avec le numéro, région parente obligatoire, chef-lieu rattaché au bon département (ou à un département de la région), noms français, identifiants et numéros uniques, région sans département, source inconnue, superficie, population accompagnée de son année. |
| `data/RealAssetsTest` **(vraies données)** | 13 | 101 départements (96 + 5), 13 + 5 régions, liste exacte des numéros, identifiants `DEP-xx` / `CL-xx`, nombre de départements par région, cas particuliers (Corse, Alsace, Rhône, Paris, Basse-Terre, Pontoise…), chefs-lieux de région, **unicité des formes normalisées** dans chaque famille, abréviations « St » et alias décidés (Évry, PACA), accents, superficies et populations INSEE (rien pour Mayotte), notes administratives avec source Légifrance. |
| `data/PlaceFactsTest` | 5 | Fiches : validation, et **toutes les fiches livrées sont `reviewed`** et inscrites dans `docs/verification-fiches.md`. |
| `game/AnswerCheckerTest` **(vraies données)** | 9 | Normalisation (casse, accents, espaces, tirets, apostrophes), noms et chefs-lieux exacts, abréviations listées, numéros « 1 » = « 01 », « 2a » = « 2A », « 20 » refusé, « Évry » accepté et « Cergy » refusé, Basse-Terre et non Pointe-à-Pitre, chef-lieu de région ≠ chef-lieu de département, ordre officiel des numéros. |
| `game/QuizLevelsTest` **(vraies données)** | 9 | Types de question par catégorie ; chaque niveau configuré a des lieux ; les découpages par région, par zone et par tranche de 10 numéros couvrent chaque département **une seule fois** ; 96 / 101 / 13 / 18 ; lieux non jouables écartés ; clés de progression séparées par question. |
| `game/QuizSessionTest` | 14 | Moteur Trouver / Saisir (repris de China Map Quiz) : une seule réponse par question, retour, fin de partie, chrono et pause de la fiche, rejouer les erreurs. |
| `game/EliminateSessionTest` | 7 | Moteur Éliminer : bonne réponse éliminée, erreur qui reste sur la carte, fin de partie. |
| `game/StopwatchTest` | 3 | Chronomètre avec pause. |
| `map/HitTesterTest` | 14 | Point dans un polygone, trous, îles, tolérance, la plus petite surface l'emporte, points et lignes. |
| `map/MapViewportTest` | 6 | Zoom à deux doigts, déplacement, vue d'ensemble, vue cadrée sur la région d'un niveau. |
| `map/RealMapTest` **(vraies données)** | 12 | 101 départements et 101 chefs-lieux dans 6 cadres ; cadres disjoints ; **chaque chef-lieu dans son département** ; étiquettes à l'intérieur ; **petite couronne (75, 92, 93, 94) sélectionnable**, avec la tolérance de la vue d'ensemble ; Paris entouré de la petite couronne ; mer et marges des cartouches vides ; Corse ; nord en haut ; îles gardées. |
| `progress/ProgressTest` | 10 | Records, erreurs fréquentes (séparées par type de question), progression, lecture tolérante du JSON. |

Les tests d'interface (instrumentés, sur appareil) ne sont pas écrits : l'interface est vérifiée à la main sur l'émulateur et le téléphone.
