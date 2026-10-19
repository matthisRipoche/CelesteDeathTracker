# Data Model

Le sens de chaque champ, ses limites et les données encore manquantes sont expliqués dans
[`donnees-du-jeu.md`](donnees-du-jeu.md). Ce fichier fixe seulement le **contrat**.

## SID (String ID)

Chaque map dans Celeste est identifiée par un SID au format :

```
{levelSet}/{mapName}           → jeu de base  ex: Celeste/1-ForsakenCity
{levelSet}/{index}/{mapName}   → mod          ex: monikadsidespack/0/7-Summit
```

Le **levelSet** est le nom du mod (ou "Celeste" pour le jeu de base).
Le **mapName** est le nom de la map.

Le mod C# expose `level.Session.Area.GetSID()` qui retourne ce SID brut. **Le SID ne contient pas la
face** : elle est envoyée à part (`side`).

## Tentative (attempt)

Une tentative = une partie sur **une face d'une map**, de l'entrée dans la map à sa sortie. Le mod
fabrique un `attemptId` (UUID) à l'ouverture et le répète dans tous les événements de la tentative.
**Une mort ne termine pas la tentative** (le jeu recharge le niveau, la partie continue) : c'est le
serveur qui en déduit qu'un deathless est perdu à la première mort rattachée.

- Réussite deathless = `attempt_end` avec `reason = completed` et **aucune** mort rattachée.
- Échec = au moins une mort rattachée ; la première donne la room où la tentative s'arrête.

## DeathEvent

Envoyé par le mod à chaque mort comptée par le jeu (`registerStats = true`).
`POST /api/ingest/deaths`

```ts
type DeathEvent = {
  timestamp:     number   // Unix ms (UTC), horloge du PC du joueur
  chapter:       string   // SID complet  ex: "monikadsidespack/0/7-Summit"
  room:          string   // Nom de la room  ex: "b-05"
  deathX:        number   // Position X du joueur (pixels, repère de la map)
  deathY:        number   // Position Y du joueur (pixels, Y vers le bas)
  totalDeaths:   number   // Compteur de morts de la SAUVEGARDE (usage interne, jamais affiché)
  sessionDeaths: number   // Morts depuis le début de la partie (1 = première mort de la tentative)
  timeInRoomMs:  number   // Temps de la PARTIE en ms (Session.Time), malgré son nom
  // Contexte de tentative : absents si le mod a été activé en cours de partie ou s'il est ancien
  attemptId?:    string   // UUID de la tentative
  side?:         "A" | "B" | "C"
  saveSlot?:     number   // Emplacement de sauvegarde du jeu
}
```

## AttemptEvent

`POST /api/ingest/events` → 202. Chaque événement répète `attemptId`, `chapter`, `side` et
`saveSlot` : le serveur crée la tentative à n'importe quel événement reçu, donc la perte d'un
événement (envoi sans file d'attente) ne casse pas la reconstruction.

```ts
type AttemptEvent = {
  type:       "attempt_start" | "room_enter" | "attempt_end"
  attemptId:  string            // UUID, max 36 car.
  chapter:    string            // SID complet
  side:       "A" | "B" | "C"
  saveSlot?:  number
  room:       string            // start: room de départ · enter: room d'arrivée · end: room de sortie
  timestamp:  number            // Unix ms (UTC)
  sessionTimeMs: number         // Temps de la partie au moment de l'événement
  // attempt_start seulement
  mapName?:              string // Nom officiel affiché dans le jeu (traduit), sinon absent
  startedFromBeginning?: boolean
  // attempt_end seulement
  reason?: "completed" | "restart" | "give_up" | "save_and_quit" | "golden_restart" | "switched"
}
```

| Événement | Source dans le jeu |
|---|---|
| ouverture de la tentative | `Everest.Events.Level.OnLoadLevel` (première fois pour une map/face) |
| `room_enter` | `Everest.Events.Level.OnTransitionTo` |
| `attempt_end` | `Everest.Events.Level.OnExit` (motif tiré de `LevelExit.Mode`), ou changement de session (ci-dessous) |

**Le redémarrage rapide (touche « recommencer » après une mort) ne déclenche pas `OnExit`** (constaté
en jeu) : le jeu réutilise le même objet `Session` en remettant son temps et ses morts à zéro. Le mod
mémorise les dernières valeurs vues (temps, morts, room) et considère qu'il y a redémarrage quand le temps
ou le nombre de morts **recule** (ou que l'objet `Session` change). Même map et même face = `restart` ;
autre map ou face sans sortie intermédiaire = `switched`. L'ancienne tentative est close avec ses dernières
valeurs (room et temps de la dernière mort). Un respawn après une mort laisse ces valeurs monter.
Le menu pause « recommencer le chapitre » passe, lui, par `OnExit` (`restart`).

`golden_restart` : le joueur est mort avec une fraise dorée, le jeu relance le chapitre.

Les tentatives sans mort ni entrée de room, terminées en quelques secondes (recommencer tout de suite),
sont enregistrées telles quelles : c'est à la lecture (pages) de les ignorer.

Le mod oublie la tentative en cours quand on change de serveur (Production / Local) : la suivante repart
avec son événement de départ sur le nouveau serveur.

Si l'événement de départ se perd, le serveur estime le départ : instant du premier événement reçu moins
le temps de partie qu'il porte.

## Tables

`attempts` (une ligne par tentative, unique par `(user_id, client_id)`), `room_entries` (entrées de
room d'une tentative) et `death` (avec `attempt_id`, `side`, `save_slot` nullables). Voir
`api/src/main/resources/db/migration/V5__attempts.sql`.

## Champs dérivés (calculés côté serveur, jamais stockés)

| Champ | Source | Description |
|---|---|---|
| `mod` | `chapter.split("/")[0]` | Nom du mod / levelSet |
| `map` | `chapter.split("/").at(-1)` | Nom de la map |
| `date` | `new Date(timestamp)` | Date lisible |
