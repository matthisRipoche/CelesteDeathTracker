---
name: deploy-mod
description: Mettre à jour le mod CelesteDeathTracker dans le jeu Celeste (compiler la DLL, la copier dans le dossier Mods, vérifier que le jeu la charge et que les données arrivent). À utiliser dès que l'utilisateur veut déployer, installer, mettre à jour ou tester le mod dans le jeu, copier la DLL, "mettre le jeu à jour", relancer le mod après une modification du code du mod (DeathTrackerModule.cs, Tracking/, Pairing/…), ou vérifier que le jeu envoie bien ses données au serveur, même s'il ne dit pas explicitement "déployer".
---

# Mettre à jour le mod dans le jeu

Le mod est du code C# compilé en une DLL que Celeste charge au démarrage. Modifier le code ne change
rien en jeu tant que la nouvelle DLL n'est pas copiée dans le dossier `Mods` du jeu réellement utilisé.
Les erreurs classiques sont de copier au mauvais endroit, de copier pendant que le jeu tourne (le fichier
est verrouillé), ou de déployer un mod qui envoie un format que le serveur ne comprend pas encore.

Ne jamais commiter la DLL ni `.previous` (ils sont dans `.gitignore`). Ce skill ne commite rien.

## 1. Le jeu doit être fermé

```powershell
Get-Process Celeste -ErrorAction SilentlyContinue | Select-Object Id, Path
```

- Aucun résultat : on continue.
- Un résultat : le jeu tourne et verrouille la DLL. Demander à l'utilisateur de le fermer, puis
  revérifier. Ne pas tuer le processus soi-même (une sauvegarde en cours pourrait être perdue).
- Le `Path` affiché révèle l'installation réellement utilisée : le comparer à celle du point 2.

## 2. Trouver le bon dossier du jeu

L'installation utilisée est `D:\steam\steamapps\common\Celeste` (Everest 1.6623, runtime net8.0).
`D:\Game_Stores\steamapps\common\Celeste` est une ancienne installation abandonnée : ne jamais y
copier la DLL. Si le jeu tournait au point 1 et que son `Path` diffère, se fier au `Path` du processus et
prévenir l'utilisateur.

Le dossier cible est `<Celeste>\Mods\CelesteDeathTracker\` (il contient `CelesteDeathTracker.dll` et
`everest.yaml`). Le mod est listé dans `Mods\blacklist.txt` par Olympus : c'est normal, ce fichier ne doit
pas être modifié.

## 3. Le serveur doit être prêt AVANT le mod

Le mod envoie ses événements dès qu'il est chargé. Si le format a changé (nouveaux champs, nouveaux types
d'événements), déployer d'abord l'API qui sait les lire, sinon les envois sont refusés (400) et les données
sont perdues sans bruit côté joueur.

| Serveur choisi dans le menu du mod | À faire avant |
|---|---|
| Local (développement) | `docker compose up -d db` puis `cd api && ./mvnw spring-boot:run` (port 5000). Flyway applique les migrations au démarrage. |
| Production | Que la dernière version de l'API soit déployée sur le VPS (la fusion sur `main` lance la CI/CD). Vérifier `https://celeste.matthisripoche.com`. |

## 4. Compiler

```bash
dotnet build CelesteDeathTracker/CelesteDeathTracker.csproj
```

- Le projet doit cibler la même version .NET que l'Everest installé (`Celeste.runtimeconfig.json`) :
  sinon erreurs CS1705. Il cible `net8.0`.
- En cas d'erreur de compilation, la montrer à l'utilisateur et s'arrêter : ne jamais copier une ancienne
  DLL en croyant déployer la nouvelle.
- Contrôler que la DLL est récente : `bin\Debug\net8.0\CelesteDeathTracker.dll` doit avoir une date
  de modification de l'instant.

## 5. Sauvegarder l'ancienne DLL, puis copier

```powershell
$game = 'D:\steam\steamapps\common\Celeste\Mods\CelesteDeathTracker'
$build = 'D:\work\CelesteDeathTracker\CelesteDeathTracker\bin\Debug\net8.0'
Copy-Item "$game\CelesteDeathTracker.dll" 'D:\work\CelesteDeathTracker\CelesteDeathTracker\CelesteDeathTracker.dll.previous' -Force
Copy-Item "$build\CelesteDeathTracker.dll" $game -Force
```

- Si `everest.yaml` a changé (version, dépendances), le copier aussi :
  `Copy-Item D:\work\CelesteDeathTracker\CelesteDeathTracker\everest.yaml $game -Force`.
- Le fichier `.pdb` n'est pas nécessaire ; l'ajouter seulement pour obtenir des numéros de ligne dans les
  traces d'erreur de `log.txt`.
- Mettre à jour `Version` dans `everest.yaml` à chaque version distribuée à d'autres joueurs.

## 6. Lancer le jeu et vérifier

Demander à l'utilisateur de lancer Celeste (Everest, pas le jeu de base). Puis lire
`D:\steam\steamapps\common\Celeste\log.txt` :

```powershell
Select-String -Path D:\steam\steamapps\common\Celeste\log.txt -Pattern 'DeathTracker|Death Tracker|Appairage' | Select-Object -Last 20
```

Ce qu'on doit voir :
- `Death Tracker chargé !` : la DLL est bien chargée.
- Pas d'erreur `DeathTracker` de type chargement ou `MissingMethodException`, qui signalent une DLL
  compilée contre une autre version du jeu.
- Au premier lancement sur un serveur sans token : `Appairage : saisis le code …` (voir le bandeau en jeu).
  Chaque serveur (Production / Local) a son propre token ; le menu du mod choisit le serveur actif.

Vérifier ensuite que les données arrivent : jouer une tentative (entrer dans une map, passer une room,
mourir ou finir), puis contrôler en base.

```bash
docker compose exec db psql -U celeste -d celeste -c "select id, chapter, side, map_name, end_reason, started_at from attempts order by id desc limit 5;"
docker compose exec db psql -U celeste -d celeste -c "select id, room, attempt_id, side from death order by id desc limit 5;"
```

La première commande doit montrer la tentative avec sa face et le nom de la map ; la seconde, la mort
rattachée (`attempt_id` non nul). En production, passer par le site plutôt que par la base.

## 7. En cas de problème

- **Retour en arrière** : recopier `CelesteDeathTracker.dll.previous` à la place de la DLL du jeu (jeu fermé).
- **Rien ne s'envoie** : token absent pour ce serveur (relancer l'appairage depuis le menu du mod), serveur
  injoignable (le mod ignore les erreurs en silence : le jeu ne doit jamais être bloqué), ou format refusé
  (chercher les 400 dans les logs de l'API).
- **Le jeu plante au démarrage** : retirer la DLL du dossier `Mods\CelesteDeathTracker\`, ou restaurer la
  précédente, puis lire `log.txt`.
- **Comportement en jeu non vérifié** : le `CLAUDE.md` signale que les tentatives (une par partie, motif
  `restart` reçu) n'ont pas encore été confirmées en jouant. Le test du point 6 sert aussi à cela : le
  noter dans le compte rendu s'il montre un écart.

## 8. Compte rendu à l'utilisateur

Indiquer : le dossier du jeu utilisé, la date de la DLL copiée, ce qui a été vérifié dans `log.txt` et en
base, et ce qui reste à confirmer en jouant.
