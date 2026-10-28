# Plan de la version 3 : parcs nationaux et collectivités d'outre-mer

Rédigé le 28 octobre 2026. Choisi avec moi parmi les pistes de la version 3 et les idées notées (CLAUDE.md, section 17). **Fait le 28 octobre 2026** (143 tests, essayé sur l'émulateur).

## 1. Parcs nationaux

### Source

| Source | Verdict |
|---|---|
| **Parcs nationaux de France (OFB), « Contours des 11 Parcs nationaux de France »**, fichier `pnx-coeur-aa-ama.geojson` (data.gouv.fr, mis à jour le 11 février 2026), WGS 84 | **Retenue.** Licence Ouverte 2.0. Producteur officiel. Pour chaque parc : cœur terrestre, cœur marin, aire d'adhésion, aire maritime adjacente, avec le décret de création, la date et la surface. |
| INPN (MNHN), programme « Espaces protégés » | Écartée : même contenu, redistribué ; la source de l'OFB est plus directe. |
| WDPA | Exclue (redistribution interdite, CLAUDE.md section 6). |

### Décisions (à revoir avec moi si besoin)

1. **11 parcs nationaux** : Vanoise, Port-Cros, Pyrénées, Cévennes, Écrins, Mercantour, Guadeloupe, La Réunion, Parc amazonien de Guyane, Calanques, Forêts.
2. **On dessine le cœur du parc** (terrestre et marin), la zone réellement protégée. L'aire d'adhésion (communes partenaires, bien plus vaste) n'est pas dessinée. Port-Cros et les Calanques ont un grand cœur marin : ils apparaissent en mer.
3. **Nom** officiel (« Parc national de la Vanoise ») ; alias sans « Parc national » pour le mode Saisir (« Vanoise »), utilisé aussi pour les noms sur la carte.
4. **Fiche** : date de création, surface du cœur (somme des surfaces officielles du cœur terrestre et marin), départements où se trouve le cœur terrestre, et en note le décret de création. La fiche d'un département liste ses parcs nationaux.
5. **Niveaux** : métropole (8), outre-mer (3), tous (11). Question : le nom.

## 2. Collectivités d'outre-mer

### Sources

| Besoin | Source |
|---|---|
| Liste, numéros, noms | INSEE, COG 2026 (`v_comer_2026.csv`) |
| Contours | IGN ADMIN EXPRESS COG 2026 généralisé par Etalab : le fichier des départements déjà utilisé contient aussi 975 à 989 |
| Chefs-lieux | Wikidata (CC0) : propriété « capitale » (P36) de chaque collectivité et coordonnées du chef-lieu, révisions fixées |
| Statut | Constitution, article 72-3 (site du Conseil constitutionnel) : Saint-Barthélemy, Saint-Martin, Saint-Pierre-et-Miquelon, les îles Wallis et Futuna et la Polynésie française sont régis par l'article 74 ; la Nouvelle-Calédonie par le titre XIII |

### Décisions (à revoir avec moi si besoin)

1. **6 collectivités** : Saint-Pierre-et-Miquelon (975), Saint-Barthélemy (977), Saint-Martin (978), Wallis-et-Futuna (986), Polynésie française (987), Nouvelle-Calédonie (988, collectivité à statut particulier). **Écartés** : les Terres australes et antarctiques françaises (984) et Clipperton (989), qui ne sont pas des collectivités (article 72-3, 4ᵉ alinéa) et n'ont pas d'habitants permanents. À rediscuter.
2. Chaque collectivité a un **numéro** (975…), un **nom** et un **chef-lieu** (Saint-Pierre, Gustavia, Marigot, Mata-Utu, Papeete, Nouméa) : les trois mêmes questions que les départements.
3. **Une nouvelle catégorie « Outre-mer »** regroupe les 5 départements d'outre-mer et les 6 collectivités. Niveaux : départements d'outre-mer, collectivités d'outre-mer, tout l'outre-mer. La catégorie Départements ne change pas (toujours 101).
4. **Carte** : une seconde rangée de 6 cartouches sous celle des départements d'outre-mer, chacun à sa propre échelle. Elle n'apparaît (et la carte ne s'élargit) que dans la catégorie Outre-mer. Dans un cartouche qui ne contient qu'un lieu jouable, toucher n'importe où dans le cartouche le sélectionne : indispensable pour la Polynésie, dont les îles sont minuscules à cette échelle (et pratique pour la Guadeloupe ou Mayotte).
5. **Fiche** : numéro, chef-lieu, statut en note (avec sa source).
6. **Pas de population ni de superficie** pour l'instant : l'INSEE ne les publie pas dans les mêmes fichiers que pour les départements (recensements à d'autres dates). À ajouter avec une source citée si on le veut.
7. **Pas de cours d'eau, massifs ou parcs** dans les collectivités.

## 3. Fichiers

- `tools/build_parks.py` → `assets/geo/parcs.json`, `parcs.geometry.json`.
- `tools/build_overseas.py` → `assets/geo/outre-mer.json`, `outre-mer.geometry.json` (avec ses cadres).
- Modèle : `FeatureType.PARK`, `FeatureType.COLLECTIVITY` ; catégories de jeu `PARKS` et `OVERSEAS` (une catégorie de jeu peut réunir plusieurs familles de lieux).
- Carte : cadres fusionnés entre fichiers, cadres masqués quand ils sont vides, toucher dans un cartouche.
- Parcs sans contour noir (à cette échelle, le trait noircissait ces petites zones très découpées).
- Dans les jeux, les parcs sont **violets** et non verts : en vert, on ne les distinguait pas de la bonne réponse (retour du 28 octobre 2026). Ils restent verts dans Apprendre, où il n'y a pas de couleur de réponse.
- Tests : `RealParksAndOverseasTest` (6), 1 test de validation de plus dans `GeoCatalogLoaderTest` ; la catégorie Départements reste à 101 départements (107 chefs-lieux au total).

## 4. Pistes restantes

Grandes villes, lacs, îles, arrondissements de Paris, sous-préfectures ; Terres australes et Clipperton ; « France totale » sur une carte du monde ; mode Contours ; population et superficie des collectivités.
