# CelesteDeathTracker

Mod Everest pour Celeste qui envoie chaque mort du joueur à un dashboard web.
Dépôt Git (branche `main`). Documentation et commentaires en français.
**Chaque commit passe par le skill `commit`** (`.claude/skills/commit/SKILL.md`) : Conventional Commits en français, commits atomiques, build + tests avant.

## Objectif et contexte

- **But final** : une **plateforme multi-utilisateurs** (comptes, tokens d'API, stats par joueur), pas un outil local.
- **Objectif personnel de l'utilisateur** : *apprendre* Angular et Spring Boot avec ce projet. Privilégier un mode guidé : expliquer, poser le squelette et une tranche verticale, laisser l'utilisateur écrire la suite, puis relire. Ne pas tout coder d'un coup sans demande.
- Éviter de passer du temps sur la config au détriment de l'apprentissage (Postgres via Docker, pas de sur-outillage).

## État actuel vs cible

| | Actuel (prototype, fonctionnel) | Cible |
|---|---|---|
| Serveur | `dashboard-legacy/server.js` (Express 5) et `server.py` (Flask), **obsolètes** | **Spring Boot 4.1 (Java 21)**, Spring Security, JPA, Flyway |
| Front | `dashboard-legacy/public/index.html` (vanilla, polling 3 s), **obsolète** | **Angular 20** (standalone, signals), `ng2-charts` |
| Stockage | `deaths.json` | **PostgreSQL 17 + Flyway** (EN PLACE, via Docker) |
| Temps réel | polling | SSE (`SseEmitter`), par utilisateur |
| Auth | aucune | site : login (session/JWT) ; mod : token d'API Bearer |

`dashboard/` a été renommé en `dashboard-legacy/` (obsolète, marqué par `LEGACY.md`, gardé pour référence et pour `deaths.json`). Structure :

```
api/   # Spring Boot 4.1 (Maven, Java 21) : controller / repository / model / dto — EXISTE
web/   # Angular 20 (standalone, signals, RxJS), proxy /api -> :5000 — EXISTE
```

Fait : **appairage par code jeu↔compte (plus de copier-coller de token)** + limitation de débit ; **tokens d'API + `user_id` sur les morts + ingestion `/api/ingest/**` + page Angular « Tokens d'API » + mod C# (ServerUrl/ApiToken)** ; **comptes + login par session** (étape 1 du parcours : `AppUser`, `/api/auth/{register,login,logout,me}`, BCrypt, CSRF cookie, Angular router + guards + intercepteur + page login) ; migration H2 → PostgreSQL + Flyway et dockerisation (db/api/web, healthchecks) ; interface Angular v1 (cartes, classement des rooms, par chapitre, dernières morts, états chargement/erreur/vide) ; API minimale `POST/GET /api/deaths` (entité `Death`, `DeathRepository`, DTO `DeathRequest`/`DeathResponse`, validation, H2 fichier, port 5000), testée au `curl`. Reste : `/api/stats`, `/api/events`, import de `dashboard-legacy/deaths.json`.

Parcours prévu : ~~(1) User + login~~ FAIT, ~~(2) tokens + filtre d'ingestion~~ FAIT, ~~(3) ingestion rattachée au compte~~ FAIT, ~~(4) front login + tokens~~ FAIT, (4b) `GET /api/stats` (GROUP BY JPQL) côté Spring à la place des calculs du navigateur, (5) SSE, graphiques, heatmap, (6) OAuth Discord/Steam, pairing par code, profils publics.

Questions encore ouvertes : « notifications » vers l'utilisateur (Discord/email/push) ou seulement flux live ? Données privées par compte ou profils publics/classements ?

## Architecture actuelle

```
Celeste (jeu) ──[mod C#]──HTTP POST──▶ dashboard (Express | Flask) ──▶ deaths.json
                                              ▲
                                  navigateur (public/index.html, poll /api/stats toutes les 3 s)
```

| Dossier | Rôle |
|---|---|
| `CelesteDeathTracker/` | Mod C# (net8.0, Everest). Point d'entrée : `DeathTrackerModule.cs`. `Class1.cs` est vide/supprimable. |
| `api/` | **API Spring Boot** (Maven, `./mvnw`). Package `com.celestedeathtracker.api`. Étape 1 faite : `POST/GET /api/deaths`. PostgreSQL + Flyway (`db/migration/V1__create_death.sql`), `ddl-auto=validate` ; H2 en mémoire seulement pour les tests. Colonnes mappées explicitement avec `@Column`. |
| `docker-compose.yml` | Pile db (postgres:17) + api + web (nginx). `docker-compose.override.yml` (dev) publie 5432. `.env` (ignoré) : `POSTGRES_PASSWORD`, `WEB_PORT`. Voir `docs/docker-and-deployment.md`. |
| `web/` | **Front Angular 20** (CLI 20 car Node 24.12 < 24.15 exigé par le CLI récent). Dashboard v1 : `death.model.ts`, `death.service.ts`, `app.ts/html/css`. Stats calculées côté client depuis `GET /api/deaths`, poll 3 s via `toSignal(timer…)`. |
| `dashboard-legacy/` | **OBSOLÈTE** : ancien prototype Express/Flask + `deaths.json`. Ne pas lancer avec `api/` (même port 5000). |
| `docs/data-model.md` | Contrat de données `DeathEvent` / `SessionEvent` / SID. À lire avant de toucher aux payloads. |
| `docs/auth-and-tokens.md` | Auth du site vs mod, cycle de vie et stockage des tokens d'API, règles de sécurité. |

## Commandes (prototype)

```bash
# Mod (nécessite Celeste + Everest ; CELESTE_PATH par défaut D:\Game_Stores\steamapps\common\Celeste)
dotnet build CelesteDeathTracker/CelesteDeathTracker.csproj
dotnet build -p:CELESTE_PATH="C:\chemin\Celeste"

# API Spring Boot (JDK 21 + Maven présents)
# Pile complète en Docker : http://localhost:8090 (site), :5000 (API pour le mod)
docker compose up -d --build
docker compose down            # stoppe (données conservées) ; down -v supprime la base

# Dev hors Docker : seulement la base en Docker, puis API et front en local
docker compose up -d db
cd api && ./mvnw spring-boot:run             # http://localhost:5000

# Front Angular (lancer l API avant ; ng serve proxy /api vers :5000)
cd web && npm install && npm start        # http://localhost:4200
cd web && npx ng test --watch=false --browsers=ChromeHeadless

# Ancien dashboard (obsolète)
cd dashboard-legacy && npm install && npm start      # http://localhost:5000 (PORT env)
npm run dev                                   # node --watch
pip install -r requirements.txt && python server.py   # Flask, port 5000 fixe
```

Tests : Karma/Jasmine côté `web/` (2 tests) et un test de contexte Spring dans `api/`. Pas de linter.

## Contrat API

Navigateur (session cookie + CSRF) :
- `POST /api/auth/register` · `POST /api/auth/login` · `POST /api/auth/logout` · `GET /api/auth/me`
- `GET /api/deaths` : uniquement les morts du compte connecté
- `GET /api/tokens` · `POST /api/tokens` {label} (renvoie le token en clair UNE fois) · `DELETE /api/tokens/{id}` (révoque)

Mod C# (`Authorization: Bearer cdt_...`, sans session ni CSRF, chaîne de sécurité séparée) :
- `POST /api/ingest/deaths` : `DeathEvent` → rattaché au compte propriétaire du token (jamais d'id utilisateur dans le payload)
- `POST /api/ingest/events` : `SessionEvent` → 202, **non stocké pour l'instant**

Appairage (sans token, limité en débit, `/api/pairing/*`) :
- Mod : `POST /api/pairing/start` {deviceName} → `deviceCode` (secret) + `userCode` XXXX-XXXX + `verificationUrl` ; `POST /api/pairing/poll` {deviceCode} → `pending` / `approved`+token (UNE seule fois) / `denied` / `expired`
- Navigateur (session + CSRF) : `GET /api/pairing/lookup?code=` · `POST /api/pairing/approve` · `POST /api/pairing/deny`


Supprimés : `POST /api/deaths` (ancienne route ouverte), `/api/stats` et `DELETE /api/deaths` (jamais portés depuis le prototype).
Le mod envoie en camelCase, tous les champs de `DeathEvent` sont obligatoires (Jackson 3).

## Authentification (résumé, détails dans `docs/auth-and-tokens.md`)

- Un token d'API par machine/utilisateur, généré sur le site, affiché une seule fois, stocké **hashé (SHA-256)** en base.
- Côté mod : stocké en clair dans `DeathTrackerSettings` (YAML Everest), envoyé en `Authorization: Bearer`.
- Routes d'ingestion séparées de celles du site. Toutes les requêtes de lecture filtrées par `user_id`.
- Le mod est du code client non fiable : valider, limiter taille et débit. HTTPS obligatoire en production.

## Points d'attention

- **Garder `server.js` et `server.py` synchronisés** tant qu'ils existent (mêmes routes, mêmes réponses).
- **`dashboard-legacy/deaths.json` contient des données réelles** (23 morts enregistrées ; le `totalDeaths` ~35k dedans est le compteur du fichier de sauvegarde du jeu, pas le nombre de lignes) : ne pas l'écraser ni le réinitialiser sans demande explicite. Prévoir un script d'import vers la nouvelle base.
- `id` = `deaths.length` dans le prototype : incohérent après suppression ou édition manuelle.
- Hooks MonoMod : `On.Celeste.Player.Die`, `Level.Begin`, `Level.End` (délégués `On.*` de `MMHOOK_Celeste.dll`). Tout nouveau hook doit être désabonné dans `Unload()`.
- Envois HTTP fire-and-forget (timeout 2 s, erreurs en `Verbose`) : le jeu ne doit jamais être bloqué ou planté par le serveur. Le passage à un serveur distant demandera une file d'attente avec retry.
- `timeInRoomMs` = `Session.Time / 10_000` (ticks .NET → ms) : temps de session, pas strictement celui de la room.
- SID de mods : `levelSet/index/mapName` ; `mod` et `map` sont dérivés (stockables en colonnes indexées).
- `everest.yaml` déclare la dépendance Everest `1.0.0` : mettre `Version` à jour à chaque release.
- **Ports locaux déjà pris sur la machine de l'utilisateur** par d'autres projets Docker : 8080 (veille-laravel), 8081 (phpmyadmin). Le site de ce projet est donc sur **8090** (`WEB_PORT` dans `.env`).
- Hibernate déduit `deathx` (sans `_`) de `deathX` : toujours mapper explicitement avec `@Column(name=...)` et garder la migration Flyway cohérente. `ddl-auto=validate` fait échouer le démarrage en cas d'écart.
- Ne jamais modifier une migration Flyway déjà appliquée : créer `V2__...sql`.
- **Auth du site = session HTTP (cookie JSESSIONID HttpOnly) + CSRF par cookie `XSRF-TOKEN`/en-tête `X-XSRF-TOKEN`** (Angular le gère). `CsrfCookieFilter` est indispensable : sans lui le cookie n'est pas posé sur un GET.
- `/error` doit rester en `permitAll` : sinon toute erreur 4xx (409, 400…) devient un 401 réel, alors que MockMvc ne le montre pas. Tester les codes d'erreur aussi contre la pile Docker, pas seulement en MockMvc.
- Jackson 3 (Spring Boot 4) rejette un JSON auquel il manque un champ numérique primitif (400) : le mod doit toujours envoyer tous les champs de `DeathEvent`.
- **Deux chaînes Spring Security** (`SecurityConfig`) : `@Order(1)` `/api/ingest/**` stateless + `ApiTokenAuthFilter` + autorité `INGEST` ; `@Order(2)` le reste (session + CSRF). Un cookie de session ne donne PAS accès à l'ingestion et un token ne donne PAS accès à la lecture.
- `ApiTokenAuthFilter` n'est volontairement pas un `@Component` (sinon Boot l'enregistre sur toutes les routes).
- Tokens : `cdt_` + 32 octets aléatoires base64url (47 car.), stockés en SHA-256 (`api_tokens.token_hash`), max 10 actifs par compte, `last_used_at` mis à jour à chaque envoi (un UPDATE par mort : à alléger si le volume monte).
- 35 morts historiques en base ont `user_id` NULL (import avant les comptes) : visibles par personne. À rattacher à un compte ou supprimer avant la prod.
- Ne PAS déployer sans HTTPS (tokens en Bearer), ni sans changer `POSTGRES_PASSWORD`. Limitation de débit EN PLACE (en mémoire, une seule instance d'API) : pairing start 10/min/IP, poll 60/min/IP, lookup/approve 10/min/compte, ingestion 300/min/compte.
- **Mod C#** : `Pairing.cs` (PairingManager + PairingOverlay), `DeathTrackerModule.cs`. Au lancement sans token : demande un code, l'affiche en bandeau dans le jeu, ouvre `/pair` dans le navigateur, interroge jusqu'à recevoir le token puis le range dans les réglages (`ApiToken`, `[SettingIgnore]`). Sur 401 il oublie le token et se ré-associe. Build : `dotnet build`, puis copier la DLL dans `Celeste/Mods/CelesteDeathTracker/` (Celeste fermé ; vérifier avec `tasklist`). Ancienne DLL de référence : `CelesteDeathTracker/CelesteDeathTracker.dll.previous`.
- **Appairage** : `deviceCode` (secret du mod, hashé en base) ≠ `userCode` (court, affiché). Le token est généré au moment du poll (jamais stocké en clair avant) et la transition `APPROVED→CONSUMED` est atomique en SQL. Le code est TOUJOURS saisi à la main sur `/pair` (jamais pré-rempli par un lien : anti-phishing). `app.public-url` (env `PUBLIC_URL`) = adresse du SITE ouverte par le mod ; `ServerUrl` du mod = adresse de l'API.
- `server.forward-headers-strategy=native` : l'IP cliente vient de `X-Forwarded-For`, accepté par Tomcat seulement depuis un réseau privé (le proxy). Ne pas exposer l'API directement sur Internet derrière un autre proxy sans le vérifier.
- Piège C# : dans une classe dérivée de `DrawableGameComponent`, `Draw` désigne la méthode ; utiliser `Monocle.Draw.SpriteBatch` / `Monocle.Draw.Rect`.
- Comptes de test locaux : `joueur@celeste.fr` et `demo@celeste.fr` (mot de passe `motdepasse1`). À supprimer avant la prod.
- **Installation Celeste réellement utilisée : `D:\steam\steamapps\common\Celeste`** (Everest 1.6623, runtime **net8.0**, 23 mods + le nôtre via `Mods/blacklist.txt`). `D:\Game_Stores\steamapps\common\Celeste` est une ancienne installation inutilisée (Everest 1.5509, log de 2025) : ne plus y déployer. Toujours vérifier le chemin réel avec `Get-Process Celeste | Select Path` avant de copier la DLL.
- Le projet mod DOIT cibler la même version .NET que l'Everest installé (`Celeste.runtimeconfig.json`) : sinon erreurs CS1705 à la compilation. `CELESTE_PATH` du `.csproj` pointe vers `D:\steam\...`.
- Les logs du jeu sont dans `D:\steam\steamapps\common\Celeste\log.txt` (codes d'appairage : ligne `Appairage : saisis le code …`).
- **CI/CD** : `.github/workflows/ci-cd.yml` (tests API + site sur PR et push ; sur `main` : images GHCR `ghcr.io/<owner>/celeste-death-tracker-{api,web}:sha-…` puis déploiement SSH). Prod : `docker-compose.prod.yml` (se branche sur le Traefik existant du VPS via labels, réseau `frontend` ; PAS de Caddy) + `deploy/backup.sh`. Guide complet : `deploy/README.md`. Le mod C# n'est PAS compilé en CI (DLL du jeu non publiables).
- Historique : les premiers commits sont en texte libre, puis **Conventional Commits** à partir de `build(web): ajouter ng2-charts…`. Le dépôt GitHub distant est à créer par l'utilisateur (`gh` non connecté). `.env` est ignoré ; `dashboard-legacy/deaths.json` aussi.
- Avant la prod : `ServerUrl` du mod à passer en `https://<domaine>`, supprimer les comptes de test, mot de passe PostgreSQL fort sur le serveur.
- **Serveur de production** : VPS 57.129.139.62, domaine `celeste.matthisripoche.com` (DNS déjà en place). Il héberge d'autres applications (portfolio Next.js, vaultwarden, laravel, vue, express) derrière **Traefik v3.7** (conteneur `traefik-traefik-1`, réseau `frontend`) qui possède 80/443 : ne JAMAIS publier 80/443 ni lancer un second reverse proxy. Noms Traefik confirmés (`TRAEFIK_ENTRYPOINT`=websecure, `TRAEFIK_CERTRESOLVER`=ovhcloud — relevés sur les labels de portfolio-next) ; SSH : port 22 inaccessible depuis le poste de développement, port/filtrage IP à confirmer.
- nginx s'adresse à l'API via l'alias réseau `celeste-api` (pas `api`) car `web` est aussi sur le réseau partagé de Traefik.
- **Mod : serveur actif sélectionnable** (`ServerTarget` Production|Local, défaut Production) via le menu du mod (menu construit à la main dans `CreateModMenuSection`, tous les réglages en `[SettingIgnore]`). Un token par serveur (`ProductionToken`, `LocalToken`) ; l'ancien couple `ServerUrl`/`ApiToken` est migré dans `MigrateLegacySettings`. Changer de serveur appelle `OnServerChanged` (via le setter de `Server`) qui relance l'appairage. Les 401 sont attribués au serveur qui a émis la requête (`Reset(target)`).
- **Design system « Basecamp »** : jetons `web/src/styles/tokens.css`, composants `web/src/styles/ds.css` (classes `.ds-*`), doc `docs/design-system.md`. Vitrine **dev uniquement** : `npm start` puis `/design-system` ; route déclarée dans `src/app/dev-routes.ts`, remplacée par `dev-routes.prod.ts` en production (`fileReplacements`), assets `public/local-assets/**` exclus du build prod. Les pages existantes (dashboard, login, pair, tokens) n'ont PAS encore été migrées vers ce design system.
- **Assets du jeu = copyright** (© Extremely OK Games, aucune politique de fan content trouvée) : extraits en local par `tools/celeste-assets/`, dossier `web/public/local-assets/` ignoré par Git et Docker. Ne jamais les committer ni les servir en production. Police du jeu = Renogare (licence custom) : on utilise Outfit + Pixelify Sans (OFL).
