# Data Model

## SID (String ID)

Chaque map dans Celeste est identifiée par un SID au format :

```
{levelSet}/{mapName}           → jeu de base  ex: Celeste/1-ForsakenCity
{levelSet}/{index}/{mapName}   → mod          ex: monikadsidespack/0/7-Summit
```

Le **levelSet** est le nom du mod (ou "Celeste" pour le jeu de base).
Le **mapName** est le nom de la map.

Le mod C# expose `level.Session.Area.GetSID()` qui retourne ce SID brut.

## DeathEvent

Envoyé par le mod à chaque mort du joueur.

```ts
type DeathEvent = {
  timestamp:     number   // Unix ms (UTC)
  chapter:       string   // SID complet  ex: "monikadsidespack/0/7-Summit"
  room:          string   // Nom de la room  ex: "b-05"
  deathX:        number   // Position X du joueur (pixels)
  deathY:        number   // Position Y du joueur (pixels)
  totalDeaths:   number   // Morts totales sur le save file
  sessionDeaths: number   // Morts depuis l'entrée dans la map
  timeInRoomMs:  number   // Temps passé dans la room avant la mort (ms)
}
```

## SessionEvent

Envoyé à l'entrée et à la sortie d'une map.

```ts
type SessionEvent = {
  type:      "session_start" | "session_end"
  chapter:   string   // SID complet
  room:      string   // Room de départ / de sortie
  timestamp: number   // Unix ms (UTC)
}
```

## Champs dérivés (calculés côté serveur, jamais stockés)

| Champ | Source | Description |
|---|---|---|
| `mod` | `chapter.split("/")[0]` | Nom du mod / levelSet |
| `map` | `chapter.split("/").at(-1)` | Nom de la map |
| `date` | `new Date(timestamp)` | Date lisible |
