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
