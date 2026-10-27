# Vérification des fiches « En savoir plus »

Règle (CLAUDE.md, section 7) : **chaque phrase d'une fiche a une source citable** (article Wikipédia avec son numéro de révision, INSEE, IGN, Légifrance, site officiel). Ce qui est introuvable est **retiré**, pas remplacé par du non vérifié. Une fiche vérifiée porte `"reviewed": true` dans `app/src/main/assets/geo/facts.json`, et figure ici avec ses sources.

Deux tests le garantissent (`PlaceFactsTest`) : toutes les fiches livrées sont `reviewed`, et chacune a une entrée dans ce registre (son identifiant, par exemple `DEP-15`, doit apparaître ci-dessous).

## Méthode

1. Rédiger la fiche (origine du nom, histoire, légende, anecdotes : 300 caractères au plus par texte, 3 anecdotes au plus).
2. Pour chaque phrase, trouver la source et noter : URL, numéro de révision (Wikipédia : lien « Version imprimable » ou « Lien permanent »), date de consultation, et l'extrait qui la justifie.
3. Retirer toute phrase sans source.
4. Passer `reviewed` à `true` et ajouter la fiche au tableau.

## Fiches vérifiées

| Lieu | Rubrique | Source (URL, révision) | Consultée le |
|---|---|---|---|
| — | — | Aucune fiche livrée pour l'instant | — |
