# Mise en production et CI/CD

```
 git push main ─▶ GitHub Actions ─▶ tests API + site ─▶ images Docker (GHCR) ─▶ SSH ─▶ VPS
                                                                                        │
                                              Caddy (HTTPS) ─▶ nginx (site) ─▶ API ─▶ PostgreSQL
```

Chaque push sur `main` qui passe les tests est déployé automatiquement. Les pull requests ne font
que lancer les tests.

## 1. Une seule fois : préparer le serveur

Sur le VPS (Debian/Ubuntu), en root ou avec sudo.

### Docker
```bash
curl -fsSL https://get.docker.com | sh
```

### Utilisateur dédié au déploiement
```bash
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy      # attention : le groupe docker équivaut à root sur cette machine
mkdir -p /opt/celeste && chown deploy:deploy /opt/celeste
```

### Pare-feu : seuls SSH, HTTP et HTTPS
```bash
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp
ufw enable
```
(PostgreSQL et l'API ne sont pas publiés : rien à ouvrir pour eux.)

### DNS
Chez ton registrar, un enregistrement **A** pour le sous-domaine (ex. `celeste`) vers l'IP du VPS.
Caddy ne peut obtenir le certificat qu'une fois cet enregistrement propagé.

### Fichier de configuration du serveur
```bash
sudo -u deploy nano /opt/celeste/.env
```
```env
DOMAIN=celeste.tondomaine.fr
POSTGRES_PASSWORD=<mot de passe long et aléatoire : openssl rand -base64 32>
```
`IMAGE_PREFIX` et `TAG` sont ajoutés automatiquement par le déploiement.

## 2. Une seule fois : clé SSH de déploiement

Sur **ta machine**, une clé réservée à la CI (ne réutilise pas ta clé personnelle) :
```bash
ssh-keygen -t ed25519 -f deploy_key -N "" -C "github-actions-deploy"
ssh-copy-id -i deploy_key.pub deploy@<ip-du-vps>      # ou ajouter deploy_key.pub à ~deploy/.ssh/authorized_keys
ssh-keyscan -p 22 <ip-du-vps> > known_hosts_vps       # empreinte du serveur
```
Puis **supprime** `deploy_key` de ta machine après l'avoir copiée dans GitHub.

## 3. Une seule fois : GitHub

1. Crée un dépôt **privé** et pousse le projet (`git remote add origin ...`, `git push -u origin main`).
2. *Settings → Environments → New environment* : `production`
   (optionnel mais recommandé : « Required reviewers » = toi, pour valider chaque déploiement).
3. Dans cet environnement, ajoute les **secrets** :

| Secret | Valeur |
|---|---|
| `SSH_HOST` | IP ou nom du VPS |
| `SSH_USER` | `deploy` |
| `SSH_PORT` | (facultatif) port SSH si différent de 22 |
| `SSH_PRIVATE_KEY` | contenu du fichier `deploy_key` |
| `SSH_KNOWN_HOSTS` | contenu de `known_hosts_vps` |
| `DOMAIN` | `celeste.tondomaine.fr` |

   Et en **variables** (*Settings → Secrets and variables → Actions → Variables*) :

   | Variable | Valeur |
   |---|---|
   | `DEPLOY_ENABLED` | `true` : **interrupteur du déploiement**. Tant qu'elle n'existe pas, un push sur `main` lance les tests et construit les images, mais ne déploie rien. |
   | `DEPLOY_PATH` | (facultatif) si différent de `/opt/celeste` |
4. *Settings → Actions → General → Workflow permissions* : « Read and write permissions »
   (nécessaire pour publier les images dans GHCR).

## 4. Premier déploiement

Un push sur `main` suffit. Suis-le dans l'onglet *Actions*. À la fin, `https://<domaine>` répond.
À la première exécution, Caddy demande le certificat : compte une minute.

Puis crée ton compte sur le site, et dans le mod renseigne l'adresse du serveur (voir §7).

## 5. Au quotidien

| Besoin | Action |
|---|---|
| Déployer | `git push` sur `main` (après PR et tests verts) |
| Voir les logs | `docker compose -f docker-compose.prod.yml logs -f api` |
| État | `docker compose -f docker-compose.prod.yml ps` |
| **Revenir en arrière** | `cd /opt/celeste && sed -i 's/^TAG=.*/TAG=sha-<ancien>/' .env && docker compose -f docker-compose.prod.yml up -d` (les tags `sha-…` des déploiements précédents restent dans GHCR) |
| Sauvegarde manuelle | `/opt/celeste/backup.sh` |

### Sauvegardes automatiques
```bash
sudo -u deploy crontab -e
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

## 6. Sécurité : ce qu'il faut savoir

- **Rien n'est exposé** sauf 80/443. L'API et la base ne sont joignables que depuis le réseau Docker.
- Les secrets ne sont **jamais dans Git** : `.env` du serveur et secrets GitHub uniquement.
- Le jeton GitHub qui sert à télécharger les images est temporaire (durée du job) et transmis par
  stdin : il n'est pas conservé sur le serveur.
- La clé SSH de déploiement ne sert qu'à ça ; si elle fuit, retire-la de `authorized_keys`.
- Mettre à jour régulièrement le serveur (`apt upgrade`) et les images (`docker compose pull` est fait à chaque déploiement).
- Limitation de débit en mémoire : adaptée à **une seule instance** de l'API.

## 7. Configurer le mod pour la production

Dans `DeathTrackerSettings`, `ServerUrl` doit valoir `https://celeste.tondomaine.fr` (sans `/api`).
Il est modifiable dans *Mod Options* ou dans
`<Celeste>/Saves/modsettings-CelesteDeathTracker.celeste` :
```yaml
ServerUrl: https://celeste.tondomaine.fr
```
Au lancement suivant, le mod ouvrira `https://celeste.tondomaine.fr/pair` et affichera le code.
(Si tu réutilises ce poste pour le développement local, remets `http://localhost:5000`.)

## 8. Ce que la CI ne fait pas

- **Compiler le mod C#** : il dépend des DLL de Celeste, qui ne peuvent pas être publiées. Le mod se
  compile et se déploie à la main (`dotnet build` puis copie dans `Mods/`).
- **Tests de bout en bout dans le navigateur** : seuls les tests unitaires et d'intégration tournent.
