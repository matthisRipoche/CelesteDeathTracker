# Live de session

Le live montre, **pendant que le joueur joue**, la tentative en cours et les statistiques de la session.
Page `/live` (« Direct ») et bandeau doré « En jeu » sur toutes les pages tant qu'une tentative est ouverte.

## Ce qu'est une session

Une **session** est une suite de tentatives, toutes faces confondues, dont deux voisines ne sont pas séparées
de plus de **30 minutes d'inactivité**. Mais le jeu n'envoie **rien pendant une pause** : tant que la dernière
tentative est ouverte (le joueur n'a pas quitté la map), la session reste donc ouverte, jusqu'à **3 heures**
sans nouvelle (au-delà, le jeu est tenu pour fermé sans passer par la sortie de la map).

| Situation | Session |
|---|---|
| Tentative ouverte, dernier événement il y a moins de 3 h (pause comprise) | **Active**, la tentative est « en cours » |
| Tentative fermée (sortie de map, redémarrage, fermeture du jeu) | Active encore 30 min, puis terminée |
| Plus de 30 min entre la fin d'une tentative et le départ de la suivante | Nouvelle session |
| Rien joué depuis plus de 24 h | Pas de session |

Les constantes sont dans `LiveService` (`SESSION_GAP_MIN`, `PAUSE_TOLERANCE_H`).

## Ce qui est calculé (`LiveService`)

- **Tentative en cours** : face, room actuelle (dernière entrée de room), départ, dernier événement reçu, morts,
  rooms traversées. Le chrono tourne côté navigateur, recalé sur `serverTime`.
- **Session** : tentatives (significatives, mêmes règles que les faces, voir `AttemptViews`), deathless réussis,
  morts, meilleur temps réussi, plus longue tentative, temps de jeu actif, faces jouées, **passage clé** (la room
  où au moins deux tentatives de la session se sont arrêtées), et les 8 dernières tentatives.

## API

| Route | Rôle |
|---|---|
| `GET /api/live` | L'état courant, une fois (premier affichage, secours) |
| `GET /api/live/stream` | Flux **Server-Sent Events** : un événement `snapshot` à l'ouverture, puis à chaque événement du jeu ; un `ping` toutes les 15 s ; l'état renvoyé chaque minute pour que « session terminée » apparaisse sans nouvel événement |

Réservé à la session de connexion du navigateur (un token d'API ne donne pas accès au live). Le mod n'appelle
jamais ces routes : chaque événement d'ingestion (`/api/ingest/*`) déclenche `LiveHub.publish`.

## Temps réel

- **Un seul état complet** par message (`snapshot`), pas de différences : simple, et une reconnexion se rattrape
  toute seule (le navigateur se reconnecte via `EventSource`, le serveur renvoie l'état).
- **Côté site** : `LiveFeed` (`core/live-feed.ts`) ouvre **une** connexion partagée par toutes les pages, la garde
  10 s après la dernière page, et fait tourner l'horloge chaque seconde seulement s'il y a une tentative ouverte.
- **Proxys** : l'API répond `X-Accel-Buffering: no` et `web/nginx.conf` a un bloc dédié
  (`location = /api/live/stream`, `proxy_buffering off`, `proxy_read_timeout 1h`). Sans cela, nginx garderait la
  réponse en mémoire tampon et rien n'arriverait. Traefik ne met pas les réponses en mémoire tampon par défaut.
- **Limite** : les connexions sont gardées en mémoire de l'instance d'API (comme la limitation de débit). Une
  seule instance suffit ; plusieurs demanderaient un bus de messages (Redis, base…). 5 flux au maximum par compte.

## Cas particuliers

- **Fermeture du jeu** : le mod envoie `attempt_end` avec `reason = game_closed` (`Everest.Events.Celeste.OnExiting`).
  Un **plantage** n'envoie rien : le bandeau reste jusqu'à 3 h ou jusqu'à la prochaine tentative.
- **Pause longue** : après 90 s sans événement, la page Direct indique que le jeu est peut-être en pause.
- **Plusieurs onglets** : tous reçoivent la mise à jour (un message par onglet).

## À faire plus tard

- Overlay public pour le stream (page légère à mettre dans OBS), avec un lien secret par compte.
- Signal explicite de pause envoyé par le mod (pour distinguer pause et jeu fermé après un plantage).
