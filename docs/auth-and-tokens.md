# Authentification et tokens d'API

Le projet vise une **plateforme multi-utilisateurs** : chaque joueur a un compte, et les morts envoyées par le mod doivent être rattachées au bon compte.

## Deux authentifications distinctes

| Qui | Mécanisme | Routes |
|---|---|---|
| Navigateur (Angular) | Login + session ou JWT (OAuth Discord/Steam possible plus tard) | `/api/**` (site) |
| Mod C# (jeu) | Token d'API en `Authorization: Bearer <token>` | `/api/ingest/**` |

Les règles de sécurité diffèrent : les routes d'ingestion sont séparées de celles du site, avec leur propre filtre Spring Security.

## Cycle de vie d'un token

1. L'utilisateur se connecte au site et demande un token (avec un `label`, ex. « PC fixe »).
2. Le serveur génère 32 octets aléatoires (`SecureRandom`), encodés en Base64 URL-safe.
3. Le token est **affiché une seule fois** à l'utilisateur.
4. Le serveur ne conserve que `SHA-256(token)`.
5. L'utilisateur colle le token dans les réglages du mod.
6. À chaque requête, le serveur hashe le token reçu et le cherche par `token_hash`, en vérifiant `revoked = false`.
7. L'utilisateur peut révoquer ou régénérer un token depuis le site.

## Stockage

### Serveur : en base, hashé

```
api_tokens(id, user_id, token_hash UNIQUE, label, created_at, last_used_at, revoked)
```

- Jamais de token en clair en base ni dans les logs.
- SHA-256 simple suffit : le token a une entropie élevée. bcrypt/argon2 sont inutiles et empêcheraient la recherche indexée par hash.
- Comparer les hashes en temps constant si la comparaison se fait en code (`MessageDigest.isEqual`).

### Joueur : dans les réglages du mod

`DeathTrackerSettings` (fichier YAML géré par Everest, dossier `Saves` de Celeste) :

```csharp
[SettingName("DEATH_TRACKER_TOKEN")]
public string ApiToken { get; set; } = "";
```

Le token est donc en clair sur le disque du joueur. C'est acceptable car il ne permet que l'envoi de morts pour ce compte (pas de lecture, pas de changement de mot de passe) et il est révocable.

## Modèle de données minimal

```
users(id, email, password_hash, created_at)
api_tokens(id, user_id, token_hash, label, created_at, last_used_at, revoked)
deaths(id, user_id, timestamp, chapter, room, death_x, death_y,
       total_deaths, session_deaths, time_in_room_ms)
sessions(id, user_id, chapter, started_at, ended_at)
```

Le contenu de `deaths` suit `docs/data-model.md`. Les champs `mod` et `map` peuvent être stockés en colonnes indexées (dérivés du SID).

## Règles de sécurité

- **HTTPS obligatoire** en production, sinon les tokens circulent en clair.
- **Isolation par utilisateur** : toute requête de lecture filtre sur `user_id` (source de faille la plus fréquente).
- **Le mod n'est pas fiable** : valider les payloads, limiter leur taille et le débit par token (rate limiting).
- `last_used_at` mis à jour à l'usage, visible par l'utilisateur pour détecter un usage inattendu.
- Plusieurs tokens par utilisateur autorisés (un par machine).
- Mots de passe du site : bcrypt/argon2 via `PasswordEncoder` de Spring Security.

## Changements côté mod

- `ServerUrl` configurable à la place de `localhost:{port}` codé en dur.
- Header `Authorization: Bearer <ApiToken>` sur chaque POST.
- Ne rien envoyer si le token est vide.
- À terme : petite file d'attente avec retry pour les envois échoués (le fire-and-forget actuel perd les morts hors connexion).

## Évolution possible

**Pairing par code** (comme la connexion d'une TV) : le mod affiche un code court, l'utilisateur le saisit sur le site, et le token est échangé automatiquement sans copier-coller.

## État d'implémentation

| Élément | État |
|---|---|
| Comptes (`users`, BCrypt), `POST /api/auth/register`, `login`, `logout`, `GET /api/auth/me` | **Fait** (session cookie + CSRF) |
| Front : page login/inscription, garde de routes, intercepteur 401 | **Fait** |
| `GET /api/deaths` réservé aux utilisateurs connectés | **Fait** |
| Table `api_tokens`, génération/révocation depuis le site (`/api/tokens`, page Angular) | **Fait** |
| Filtre `Authorization: Bearer` sur `/api/ingest/**` (chaîne de sécurité dédiée, sans session) | **Fait** |
| `death.user_id` + lecture filtrée par utilisateur | **Fait** |
| Mod : `ServerUrl` + `ApiToken` dans les réglages, en-tête Bearer, silence si pas de token | **Fait** (DLL à copier dans `Mods/`) |
| Limitation de débit (ingestion, pairing, tentatives de code) | **Fait** (en mémoire) |
| Persistance des `SessionEvent` | À faire |
| Pairing par code (au lieu du copier-coller) | **Fait** (voir ci-dessous) |
| Connexion et rattachement Discord (OAuth2, scope `identify`) | **Fait** (voir ci-dessous) |

Choix retenu pour le site : **session par cookie** plutôt que JWT (même domaine, cookie HttpOnly non lisible par JavaScript, révocation immédiate côté serveur).

## Connexion Discord

Bouton « Continuer avec Discord » sur la page de login, et « Rattacher mon Discord » sur le profil. Actif seulement si `DISCORD_CLIENT_ID` et `DISCORD_CLIENT_SECRET` sont définis.

- **Flux** : `GET /api/auth/oauth2/discord` redirige vers Discord ; retour sur `GET /api/auth/oauth2/callback/discord` ; Spring Security échange le code, puis `DiscordOAuth2UserService` retrouve ou crée le compte. La session et le cookie sont les mêmes qu'avec le mot de passe : le reste du site, les tokens d'API et l'appairage ne changent pas.
- **Données demandées** : scope `identify` seulement (identifiant, pseudo, avatar). Pas d'email : un compte créé par Discord a `email` et `password_hash` à NULL.
- **Identité de session = ID du compte** (et non plus l'email), car un compte Discord peut ne pas avoir d'email. `CurrentUser.user(auth)` est le seul endroit qui la traduit en compte.
- **Rattachement** : `POST /api/auth/discord/link` (session + CSRF) note en session quel compte le demande, puis le front envoie vers Discord. Refusé si ce Discord appartient à un autre compte ou si le compte a déjà un autre Discord.
- **Adresses de retour à déclarer chez Discord** : `https://celeste.matthisripoche.com/api/auth/oauth2/callback/discord` (prod), `http://localhost:8090/api/auth/oauth2/callback/discord` (Docker), `http://localhost:4200/api/auth/oauth2/callback/discord` (`ng serve`).
- **Après la connexion**, retour à la page demandée avant (ex. `/pair`), mémorisée en `sessionStorage` côté front.
- Pas encore de « détacher Discord ».

## Appairage par code (méthode normale)

Aucun token à copier : le jeu s'associe tout seul au compte.

```
 Jeu (mod)                        Serveur                          Navigateur (connecté)
    | POST /api/pairing/start ------->|                                    |
    |<-- deviceCode (secret) + code --|                                    |
    |  (affiche K7QM-4XRD, ouvre /pair)                                    |
    |                                 |<-- lookup + approve (code saisi) --|
    | POST /api/pairing/poll -------->|                                    |
    |<-- token d'API (UNE fois) ------|                                    |
    | le range dans ses réglages      |                                    |
```

| Menace | Parade |
|---|---|
| Deviner un code court | Il faut un compte connecté qui approuve ; 32^8 combinaisons ; expire en 10 min ; 10 tentatives/min/compte |
| Voler le token en cours de route | Remis uniquement au détenteur du `deviceCode` (secret, stocké hashé), une seule fois |
| Phishing (faire approuver le code d'un inconnu) | Le code est saisi à la main, jamais pré-rempli ; la page affiche l'appareil et l'heure |
| Double génération de token | Transition `APPROVED→CONSUMED` atomique (une seule requête SQL gagnante) |
| Spam des routes publiques | Limitation de débit par IP (`start` 10/min, `poll` 60/min) |

Si un token est révoqué sur le site, le mod reçoit un 401, l'oublie et redémarre l'association.

## Installer le mod

1. `dotnet build CelesteDeathTracker/CelesteDeathTracker.csproj`
2. Fermer Celeste, copier `CelesteDeathTracker/bin/Debug/net8.0/CelesteDeathTracker.dll` vers `<Celeste>/Mods/CelesteDeathTracker/`
3. Lancer Celeste : le bandeau d'association s'affiche et la page `/pair` s'ouvre.
