# Compilation et installation

## Prérequis

- Android Studio (avec son JDK intégré, `jbr`) et le SDK Android 37.
- Python 3.10 ou plus, seulement pour régénérer les données, l'icône ou les sons (`tools/`).

## Dans Android Studio

1. *File → Open* : le dossier du projet.
2. Laisser Gradle se synchroniser (barre de progression en bas).
3. Tests : clic droit sur `app/src/test` → *Run 'Tests in…'*.
4. Lancer sur l'émulateur ou sur le téléphone (débogage USB activé) : bouton ▶.

## En ligne de commande (PowerShell)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug
```

En cas de doute sur des tests « à jour » qui n'ont pas été relancés : `.\gradlew.bat testDebugUnitTest --rerun`.

L'APK **debug** est alors dans `app/build/outputs/apk/debug/app-debug.apk`.

Installer sur l'appareil branché (ou l'émulateur lancé) :

```powershell
.\gradlew.bat installDebug
```

Capture d'écran depuis l'appareil (Git Bash : préfixer avec `MSYS_NO_PATHCONV=1` toute commande contenant `/sdcard`) :

```powershell
adb exec-out screencap -p > capture.png
```

**Erreur « Installation failed… Broken pipe »** : l'émulateur redémarrait toujours depuis une sauvegarde (*Quick Boot*) prise alors que son service d'installation était cassé. Solution appliquée le 27 octobre 2026 : démarrage à froid à chaque lancement (`fastboot.forceColdBoot=yes` dans `~/.android/avd/Medium_Phone.avd/config.ini`, ancienne version gardée en `config.ini.bak-2026-10-27`). Équivalent dans Android Studio : *Device Manager → ⋮ → Edit → Boot option : Cold boot*. Le démarrage prend environ une minute de plus.

## Debug et release

- **Debug** : signé automatiquement avec la clé de débogage d'Android Studio ; pour les essais. C'est l'APK ci-dessus.
- **Release** (plus tard) : *Build → Generate Signed App Bundle or APK → APK*, avec une clé personnelle (`.jks`, jamais versionnée : voir `.gitignore`). L'APK est dans `app/build/outputs/apk/release/`.

Pour une release GitHub : copier l'APK renommé (`france-geography-quiz-v0.1.apk`) et le texte de la release dans `release/` (ignoré par Git), puis créer la release à la main sur github.com.

## Régénérer les données

```powershell
python -X utf8 tools/download_sources.py
python -X utf8 tools/build_geo.py
python -X utf8 tools/build_icon.py
python -X utf8 tools/build_sounds.py                                     # candidats à écouter
python -X utf8 tools/build_sounds.py --choisir correct=marimba erreur=quinte
```

Puis relancer les tests : plusieurs lisent les vrais fichiers de `app/src/main/assets`.
