---
name: test-local
description: Lancer tout l'environnement local de CelesteDeathTracker (Docker Desktop, base PostgreSQL, API Spring Boot, site Angular) pour tester le mod en jeu, vérifier les données reçues, ou voir le site avec de vraies données. À utiliser dès que l'utilisateur veut "lancer le projet", "tout démarrer", "tester en local", "tester le mod", "voir si les données arrivent", relancer l'API ou le site après une modification, ou arrêter l'environnement, même s'il ne cite pas Docker.
---

# Lancer l'environnement local pour tester

Pour tester le mod en jeu, il faut un serveur local qui reçoit ses événements : une base PostgreSQL,
l'API (port 5000, celle que le mod appelle en mode « Local ») et le site. Deux modes, selon ce qu'on teste.

| Mode | Quand | Ce qui tourne |
|---|---|---|
| **A. Pile Docker (par défaut)** | Tester le mod en jeu, voir le site, vérifier les données | db + api + web dans Docker. Site `http://localhost:8090`, API `http://localhost:5000`. Une seule commande. |
| **B. Développement** | Itérer vite sur le code de l'API ou du site | Seule la base en Docker ; API (`./mvnw spring-boot:run`) et site (`npm start`) en local. Site `http://localhost:4200`. |

Préférer le mode A : il reconstruit l'API avec le code du dépôt (donc avec les dernières migrations Flyway)
et reproduit la production. Le mode B sert quand on modifie le code toutes les minutes.

## 1. Docker Desktop doit tourner

```bash
docker info > /dev/null 2>&1 && echo "docker ok" || echo "docker arrêté"
```

Si le démon est arrêté (erreur sur `dockerDesktopLinuxEngine`), le démarrer puis attendre qu'il réponde
(30 à 90 s) :

```powershell
Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
```

Puis interroger `docker info` toutes les quelques secondes (sans `sleep` long bloquant) jusqu'au succès.
Si ça ne répond pas après deux minutes, le dire à l'utilisateur au lieu d'insister.

## 2. Vérifier les ports

L'environnement a besoin de 5000 (API), 8090 (site), 5432 (base) en mode A, plus 4200 en mode B. D'autres
projets de l'utilisateur occupent 8080 et 8081 : ne pas y toucher.

```bash
netstat -ano | grep -E ":(5000|8090|5432|4200) " | grep LISTEN
```

Un port déjà pris par une ancienne instance du projet (`docker compose ps`) se libère avec
`docker compose down`. Si c'est un autre programme, prévenir l'utilisateur. Rappel : l'ancien dashboard
`dashboard-legacy` utilise aussi le port 5000 et ne doit pas tourner en même temps.

## 3a. Mode A : pile Docker

```bash
docker compose up -d --build
```

Attendre que l'API soit saine (Flyway applique les migrations au démarrage, donc le premier démarrage
après un changement de schéma prend quelques secondes de plus) :

```bash
curl -s http://localhost:5000/actuator/health
docker compose ps
```

Attendu : `{"status":"UP"}` et les trois services `healthy`. En cas d'échec : `docker compose logs api --tail 50`.
Une erreur de migration Flyway (`Validate failed`, `Migration checksum mismatch`) signifie qu'une migration
déjà appliquée a été modifiée : ne pas la corriger, créer une nouvelle migration. En dernier recours pour
un environnement de test jetable seulement, `docker compose down -v` supprime la base (les données de test
sont perdues, à confirmer avec l'utilisateur avant).

## 3b. Mode B : développement

```bash
docker compose up -d db
```

API (dans un terminal à part, en tâche de fond) — le mot de passe de la base est celui de `.env`, ne jamais
l'afficher :

```bash
cd api && DB_PASSWORD="$(grep '^POSTGRES_PASSWORD=' ../.env | cut -d= -f2-)" PUBLIC_URL=http://localhost:4200 ./mvnw spring-boot:run
```

`PUBLIC_URL=http://localhost:4200` est important : c'est l'adresse du **site** que le mod ouvre pour
l'appairage, et en mode B le site est sur 4200, pas 8090.

Site :

```bash
cd web && npm install && npm start     # http://localhost:4200, proxy /api -> :5000
```

## 4. Compte et appairage

- Compte de test local : `joueur@celeste.fr` ou `demo@celeste.fr`, mot de passe `motdepasse1` (ils existent
  tant que le volume de la base n'est pas supprimé ; sinon en créer un via la page d'inscription).
- Déployer le mod avec le skill `deploy-mod` (jeu fermé), puis dans le menu du mod choisir
  « Local (développement) ».
- Au lancement, le mod affiche un code en jeu et ouvre `/pair` sur le site : se connecter avec le compte de
  test et saisir le code. Le code se tape à la main, il n'est jamais pré-rempli.

## 5. Vérifier que les données arrivent

```bash
docker compose exec db psql -U celeste -d celeste -c "select id, chapter, side, map_name, end_reason from attempts order by id desc limit 5;"
docker compose exec db psql -U celeste -d celeste -c "select id, room, attempt_id, side from death order by id desc limit 5;"
```

Scénarios à jouer pour valider les tentatives, avec le résultat attendu en base (le skill `deploy-mod`
explique comment lire `log.txt`) :

| Scénario | Attendu |
|---|---|
| Entrer dans une map, passer 2 rooms, finir sans mourir | 1 tentative, 2 `room_entries`, `end_reason = completed`, aucune mort rattachée |
| Mourir 3 fois puis finir | Toujours **1 seule** tentative, 3 morts rattachées (une mort ne crée pas de tentative) |
| Pause → recommencer le chapitre | Fin `restart`, puis une **nouvelle** tentative |
| Pause → abandonner | `end_reason = give_up` |
| Face B (puis C) | `side = B` (ou `C`) sur la tentative et sur les morts |
| Map de mod puis map du jeu de base | `map_name` = vrai titre traduit, pas un identifiant brut |
| Sauvegarder et quitter, puis reprendre | `save_and_quit`, puis nouvelle tentative avec `started_from_beginning = false` |
| Couper l'API en pleine tentative puis la relancer | La tentative se reconstruit avec les événements suivants |

Si un scénario ne donne pas le résultat attendu, le signaler : les hooks du mod (`OnLoadLevel`,
`OnTransitionTo`, `OnExit`) reposent sur des hypothèses sur le jeu qui n'ont pas encore été confirmées.

## 6. Arrêter

```bash
docker compose down        # arrête, données conservées
docker compose down -v     # arrête ET supprime la base (demander avant)
```

En mode B, arrêter aussi l'API et `npm start` (les terminaux de fond). Ne pas laisser l'environnement
tourner quand l'utilisateur a fini : il occupe les ports 5000, 5432 et 8090.

## Compte rendu

Dire à l'utilisateur : le mode lancé, les adresses (site, API), l'état de santé constaté, le compte à utiliser,
et la prochaine action de son côté (lancer le jeu, saisir le code d'appairage).
