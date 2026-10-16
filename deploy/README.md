# Mise en production et CI/CD

Site : **https://celeste.matthisripoche.com**

```
 git push main ─▶ GitHub Actions ─▶ tests API + site ─▶ images Docker (GHCR) ─▶ SSH ─▶ VPS
                                                                                        │
                                       Traefik (déjà présent) ─▶ nginx (site) ─▶ API ─▶ PostgreSQL
```

Chaque push sur `main` qui passe les tests est déployé automatiquement (une fois `DEPLOY_ENABLED=true`).
Les pull requests ne font que lancer les tests.

## 0. Le serveur existant

Le VPS (`57.129.139.62`) héberge déjà d'autres applications derrière **Traefik v3** (conteneur
`traefik-traefik-1`, réseau Docker `frontend`), qui possède les ports 80/443 et gère les certificats
Let's Encrypt. **On ne lance donc pas de second reverse proxy** : le conteneur `web` du projet
rejoint le réseau `frontend` et se déclare à Traefik par des *labels* (voir `docker-compose.prod.yml`).

| Conteneur | Réseau | Exposé ? |
|---|---|---|
| `web` (nginx + site Angular) | privé du projet **et** `frontend` | via Traefik uniquement |
| `api` (Spring Boot) | privé du projet | non |
| `db` (PostgreSQL) | privé du projet | non |

Aucun port n'est publié sur l'hôte : l'API et la base ne sont joignables par aucun autre conteneur du serveur.

## 1. Une seule fois : récupérer les noms de ton Traefik

Les labels de `docker-compose.prod.yml` utilisent des valeurs par défaut (`websecure`, `letsencrypt`,
réseau `frontend`). Elles doivent correspondre à **ta** configuration. Regarde comment une application
déjà en ligne se déclare :

```bash
docker inspect portfolio-next --format '{{range $k,$v := .Config.Labels}}{{$k}}={{$v}}{{"\n"}}{{end}}' | grep traefik
```

Cherche dans la sortie :
- `traefik.http.routers.<nom>.entrypoints=` → valeur de **`TRAEFIK_ENTRYPOINT`** (souvent `websecure`)
- `traefik.http.routers.<nom>.tls.certresolver=` → valeur de **`TRAEFIK_CERTRESOLVER`**
- `traefik.docker.network=` (s'il existe) → **`TRAEFIK_NETWORK`** (ici `frontend`)

Si aucun label `entrypoints`/`certresolver` n'apparaît, Traefik est peut-être configuré avec un
fournisseur de fichiers ou des valeurs par défaut : dans ce cas colle-moi la sortie, on adapte.

## 2. Une seule fois : préparer le dossier et la configuration

```bash
sudo mkdir -p /opt/celeste && sudo chown "$USER": /opt/celeste
nano /opt/celeste/.env
```
```env
DOMAIN=celeste.matthisripoche.com
POSTGRES_PASSWORD=<généré ci-dessous>

# Valeurs relevées à l'étape 1 (ces défauts sont utilisés si tu les omets)
TRAEFIK_NETWORK=frontend
TRAEFIK_ENTRYPOINT=websecure
TRAEFIK_CERTRESOLVER=letsencrypt
```
Générer le mot de passe **sur le serveur** (il ne doit passer ni par un chat ni par Git) :
```bash
openssl rand -base64 36 | tr -d '/+=\n' | cut -c1-40
```
`IMAGE_PREFIX` et `TAG` sont ajoutés automatiquement par chaque déploiement.

**DNS** : déjà fait. `celeste.matthisripoche.com` pointe vers `57.129.139.62` (vérifie avec `nslookup`).

## 3. Une seule fois : clé SSH de déploiement

Sur **ta machine**, une clé réservée à la CI (ne réutilise pas ta clé personnelle) :
```bash
ssh-keygen -t ed25519 -f deploy_key -N "" -C "github-actions-deploy"
ssh-copy-id -i deploy_key.pub <utilisateur>@57.129.139.62   # ou ajouter la clé publique à authorized_keys
ssh-keyscan -p <port-ssh> 57.129.139.62 > known_hosts_vps   # empreinte du serveur
```
Puis **supprime** `deploy_key` de ta machine après l'avoir copiée dans GitHub.

L'utilisateur doit pouvoir lancer `docker` (groupe `docker`, ce qui équivaut à root sur cette machine :
utilise un compte dédié si tu veux limiter l'impact).

> **Attention** : les runners GitHub ont des adresses IP variables. Si SSH est limité à certaines IP
> (pare-feu, `AllowUsers ... @ip`), le déploiement automatique sera bloqué.

## 4. Une seule fois : GitHub

1. Le dépôt privé existe déjà : `matthisRipoche/CelesteDeathTracker`.
2. *Settings → Environments → New environment* : `production`
   (optionnel mais recommandé : « Required reviewers » = toi, pour valider chaque déploiement).
3. Dans cet environnement, ajoute les **secrets** :

| Secret | Valeur |
|---|---|
| `SSH_HOST` | `57.129.139.62` |
| `SSH_USER` | utilisateur SSH du serveur |
| `SSH_PORT` | (facultatif) port SSH si différent de 22 |
| `SSH_PRIVATE_KEY` | contenu du fichier `deploy_key` |
| `SSH_KNOWN_HOSTS` | contenu de `known_hosts_vps` |
| `DOMAIN` | `celeste.matthisripoche.com` |

   Et en **variables** (*Settings → Secrets and variables → Actions → Variables*) :

   | Variable | Valeur |
   |---|---|
   | `DEPLOY_ENABLED` | `true` : **interrupteur du déploiement**. Tant qu'elle n'existe pas, un push sur `main` lance les tests et construit les images, mais ne déploie rien. |
   | `DEPLOY_PATH` | (facultatif) si différent de `/opt/celeste` |

4. *Settings → Actions → General → Workflow permissions* : « Read and write permissions »
   (nécessaire pour publier les images dans GHCR).

## 5. Premier déploiement

1. Pose `DEPLOY_ENABLED=true`.
2. *Actions → CI/CD → Re-run all jobs* (ou pousse un commit).
3. À la fin du job « Déploiement », `https://celeste.matthisripoche.com` répond. Traefik demande le
   certificat à la première requête : compte une minute.

### Si le site ne répond pas (diagnostic)
```bash
cd /opt/celeste
docker compose -f docker-compose.prod.yml ps                  # tous "healthy" / "running" ?
docker compose -f docker-compose.prod.yml logs --tail 50 api  # l'API démarre-t-elle ? (migrations Flyway)
docker logs traefik-traefik-1 --tail 50                       # Traefik voit-il le routeur ? erreur de certificat ?
docker network inspect frontend | grep -A2 celeste            # web est-il bien sur le réseau de Traefik ?
```
Causes fréquentes : mauvais nom d'entrypoint ou de certresolver (étape 1), DNS non propagé, `web`
absent du réseau `frontend`.

## 6. Au quotidien

| Besoin | Action |
|---|---|
| Déployer | `git push` sur `main` (après PR et tests verts) |
| Logs | `docker compose -f docker-compose.prod.yml logs -f api` |
| État | `docker compose -f docker-compose.prod.yml ps` |
| **Revenir en arrière** | `cd /opt/celeste && sed -i 's/^TAG=.*/TAG=sha-<ancien>/' .env && docker compose -f docker-compose.prod.yml up -d` (les tags `sha-…` restent dans GHCR) |
| Sauvegarde manuelle | `/opt/celeste/backup.sh` |

### Sauvegardes automatiques
```bash
crontab -e
# toutes les nuits à 3 h
0 3 * * * /opt/celeste/backup.sh >> /opt/celeste/backups/backup.log 2>&1
```
Pense à **copier les sauvegardes hors du serveur** (si le VPS disparaît, elles disparaissent avec lui).

### Restaurer
```bash
gunzip -c backups/celeste-AAAA-MM-JJ-HHMM.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T db psql -U celeste -d celeste
```
Les migrations (Flyway) s'appliquent seules au démarrage de l'API : un schéma ancien est mis à jour.

## 7. Sécurité : ce qu'il faut savoir

- **Rien n'est publié** par ce projet : tout passe par Traefik. L'API et la base sont sur un réseau privé.
- Les secrets ne sont **jamais dans Git** : `.env` du serveur et secrets GitHub uniquement.
- Le jeton GitHub qui sert à télécharger les images est temporaire (durée du job) et transmis par
  stdin : il n'est pas conservé sur le serveur.
- La clé SSH de déploiement ne sert qu'à ça ; si elle fuit, retire-la de `authorized_keys`.
- Le serveur héberge d'autres applications : ne modifie pas la configuration de Traefik, ce projet n'en a pas besoin.
- Limitation de débit en mémoire : adaptée à **une seule instance** de l'API.
- Les images GHCR sont **privées** ; `docker login ghcr.io` est refait à chaque déploiement.

## 8. Configurer le mod pour la production

`ServerUrl` doit valoir `https://celeste.matthisripoche.com` (sans `/api`) dans
`<Celeste>/Saves/modsettings-CelesteDeathTracker.celeste` :
```yaml
ServerUrl: https://celeste.matthisripoche.com
ApiToken: ""
```
Au lancement suivant, le mod ouvre `https://celeste.matthisripoche.com/pair` et affiche le code.
Le jeton existant (associé à ta pile locale) n'est pas valable en production : vide `ApiToken`.
Pour revenir au développement local, remets `http://localhost:5000`.

## 9. Ce que la CI ne fait pas

- **Compiler le mod C#** : il dépend des DLL de Celeste, qui ne peuvent pas être publiées. Le mod se
  compile et se déploie à la main (`dotnet build` puis copie dans `Mods/`).
- **Tests de bout en bout dans le navigateur** : seuls les tests unitaires et d'intégration tournent.
