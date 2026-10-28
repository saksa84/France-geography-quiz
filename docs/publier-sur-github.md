# Publier le projet sur GitHub (sans Claude)

Petit guide pour envoyer le code sur <https://github.com/saksa84/France-geography-quiz> et publier une release avec l'APK.

Les commandes se tapent dans un terminal ouvert dans le dossier du projet (`C:\Code\France map quiz`) : le *Terminal* d'Android Studio (en bas) ou Git Bash.

## 1. Une seule fois (déjà fait pour ce projet)

Relier le dossier local au dépôt GitHub, puis faire le premier envoi :

```bash
git remote add origin https://github.com/saksa84/France-geography-quiz.git
git push -u origin main
```

Le `-u` retient que la branche locale `main` va vers `origin/main` : ensuite, `git push` tout court suffit.

La première fois, Git Credential Manager peut ouvrir une fenêtre de connexion GitHub : se connecter dans le navigateur, c'est mémorisé.

## 2. À chaque modification

```bash
git pull                              # 1. récupérer ce qui a changé sur GitHub (README modifié sur le site…)
git status                            # 2. voir les fichiers modifiés
git add -A                            # 3. préparer tous les fichiers (sauf ceux exclus par .gitignore)
git commit -m "Ajout des montagnes"   # 4. enregistrer une étape, avec un message clair
git push                              # 5. envoyer sur GitHub
```

À retenir : **`commit` enregistre sur ton PC, `push` envoie sur GitHub**. Tant qu'il n'y a pas eu de `push`, rien n'est en ligne.

Pour voir l'historique : `git log --oneline`.

## 3. Publier une release (l'APK à télécharger)

1. **Changer le numéro de version** dans `app/build.gradle.kts` : `versionCode` augmente de 1 à chaque version (2, 3…), `versionName` est le nom affiché (« 0.2 »…). Sans cela, le téléphone peut refuser la mise à jour. Commiter et pousser.
2. **Construire l'APK** : dans Android Studio, *Build → Build App Bundle(s) / APK(s) → Build APK(s)*, ou dans le terminal :
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat testDebugUnitTest assembleDebug
   ```
   L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.
3. **Le renommer**, par exemple `france-geography-quiz-v0.2.apk` (le dossier `release/` est fait pour ça : il est ignoré par Git).
4. Sur la page du dépôt : **Releases** (colonne de droite) → **Draft a new release**.
5. **Choose a tag** : taper `v0.2` puis *Create new tag*. Donner un titre (« France Geography Quiz v0.2 ») et décrire les nouveautés.
6. Glisser l'APK dans *Attach binaries*, puis **Publish release**.

## 4. Quand ça bloque

| Message | Ce que ça veut dire | Que faire |
|---|---|---|
| `rejected … fetch first` ou `non-fast-forward` | GitHub a des changements que tu n'as pas (README modifié sur le site) | `git pull`, puis `git push` |
| `CONFLICT` après un `git pull` | Le même passage d'un fichier a été modifié des deux côtés | Ouvrir le fichier, garder la bonne version entre les marques `<<<<<<<` et `>>>>>>>`, puis `git add -A`, `git commit`, `git push` |
| `nothing to commit` | Aucun fichier n'a changé depuis le dernier commit | Rien à faire |
| `Authentication failed` | Identifiants GitHub absents ou expirés | Relancer `git push` et se reconnecter dans la fenêtre qui s'ouvre |
| `remote origin already exists` | Le dépôt est déjà relié | Rien à faire (`git remote -v` pour vérifier) |

## 5. Ce qui ne part jamais sur GitHub

Le fichier `.gitignore` exclut : les fichiers de compilation (`build/`), les téléchargements des scripts (`tools/raw/`), le dossier `release/`, les APK, les clés de signature (`.jks`) et les réglages personnels d'Android Studio. Ne jamais forcer leur ajout.
