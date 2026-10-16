# Docker et mise en production

## Services (`docker-compose.yml`)

| Service | Image | Rôle | Port hôte |
|---|---|---|---|
| `db` | `postgres:17-alpine` | Base PostgreSQL, volume nommé `pgdata` | 5432 (dev uniquement, via l'override) |
| `api` | build `./api` (Maven → JRE 21 alpine, non-root) | API Spring Boot, healthcheck `/actuator/health` | 5000 (le mod C# poste ici) |
| `web` | build `./web` (Node 22 → nginx) | Site Angular + relais `/api` vers `api:5000` | `WEB_PORT` (8090 en local) |

Ordre de démarrage : `db` sain → `api` sain → `web`. Le schéma est créé par Flyway au démarrage de l'API.

## Commandes

```bash
docker compose up -d --build          # tout construire et lancer
docker compose logs -f api            # logs
docker compose down                   # arrêter (données conservées)
docker compose down -v                # arrêter ET supprimer la base
docker compose exec db psql -U celeste -d celeste   # SQL direct

# Dev : seule la base en Docker, API et front en local
docker compose up -d db
cd api && ./mvnw spring-boot:run
cd web && npm start
```

## Configuration

`.env` (jamais committé, modèle : `.env.example`) :
- `POSTGRES_PASSWORD` : obligatoire (compose refuse de démarrer sans).
- `WEB_PORT` : port du site.

L'API lit `DB_URL`, `DB_USER`, `DB_PASSWORD` (défauts pour le dev local dans `application.properties`).

## Production

La production passe par le Traefik déjà présent sur le VPS, avec déploiement automatique via GitHub Actions.
Procédure complète, secrets, diagnostic et retour arrière : **`deploy/README.md`**.
