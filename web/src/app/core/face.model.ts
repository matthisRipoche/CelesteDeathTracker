/** Une « face » = une map + A, B ou C. Miroirs des réponses de GET /api/faces (cf. FaceDtos.java). */
export type Side = 'A' | 'B' | 'C';

/** Issue d'une tentative : réussie (deathless), morte, abandonnée ou en cours. */
export type AttemptResult = 'success' | 'death' | 'abandoned' | 'ongoing';

export interface FaceSummary {
  sid: string;
  side: Side;
  mapName: string | null;
  attempts: number;
  clears: number;
  lastPlayedAt: number;
}

export interface RoomStat {
  room: string;
  entries: number;
  endings: number;
  deaths: number;
}

export interface EvolutionPoint {
  attemptId: number;
  startedAt: number;
  durationMs: number | null;
  result: AttemptResult;
}

export interface RecentAttempt {
  id: number;
  startedAt: number;
  result: AttemptResult;
  durationMs: number | null;
  endRoom: string | null;
  roomsCleared: number;
  deaths: number;
}

export interface FaceDetail {
  sid: string;
  side: Side;
  mapName: string | null;
  attempts: number;
  clears: number;
  clearRate: number;
  bestClearMs: number | null;
  longestMs: number | null;
  longestEndRoom: string | null;
  rooms: RoomStat[];
  evolution: EvolutionPoint[];
  recent: RecentAttempt[];
}

/**
 * Une mort dans une room (GET /api/faces/room). x et y sont les coordonnées du monde de la map, l'axe Y
 * va vers le bas. first = première mort de sa tentative, celle qui fait perdre le deathless.
 */
export interface DeathPoint {
  deathId: number;
  attemptId: number;
  at: number;
  x: number;
  y: number;
  first: boolean;
}

/** Ce qui est arrivé à une tentative entrée dans la room : franchie, morte ici, ou terminée ici sans mourir. */
export type RoomOutcomeKind = 'cleared' | 'died' | 'left';

export interface RoomOutcome {
  attemptId: number;
  startedAt: number;
  outcome: RoomOutcomeKind;
}

/** stopRate = stops / entries, toujours entre 0 et 1. position commence à 1. */
export interface RoomDetail {
  sid: string;
  side: Side;
  mapName: string | null;
  room: string;
  position: number;
  roomCount: number;
  previousRoom: string | null;
  nextRoom: string | null;
  entries: number;
  stops: number;
  stopRate: number;
  deathsTotal: number;
  lastDeathAt: number | null;
  points: DeathPoint[];
  outcomes: RoomOutcome[];
}
