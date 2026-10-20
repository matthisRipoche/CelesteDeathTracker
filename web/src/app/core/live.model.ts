import { AttemptResult, Side } from './face.model';

/** Miroirs de GET /api/live (cf. LiveDtos.java). Les durées sont en millisecondes, les dates en ms Unix. */
export interface CurrentAttempt {
  id: number;
  sid: string;
  side: Side;
  mapName: string | null;
  room: string;
  startedAt: number;
  lastEventAt: number;
  deaths: number;
  roomsVisited: number;
}

export interface CruxRoom {
  sid: string;
  side: Side;
  room: string;
  endings: number;
}

export interface FaceCount {
  sid: string;
  side: Side;
  mapName: string | null;
  attempts: number;
  clears: number;
}

export interface LiveAttempt {
  id: number;
  sid: string;
  side: Side;
  mapName: string | null;
  startedAt: number;
  result: AttemptResult;
  durationMs: number | null;
  endRoom: string | null;
  deaths: number;
}

export interface SessionStats {
  startedAt: number;
  lastActivityAt: number;
  activeMs: number;
  attempts: number;
  clears: number;
  deaths: number;
  bestClearMs: number | null;
  longestMs: number | null;
  longestEndRoom: string | null;
  crux: CruxRoom | null;
  faces: FaceCount[];
  recent: LiveAttempt[];
}

/** active = la session continue (sinon c'est le résumé de la dernière). serverTime recale les chronos. */
export interface LiveSnapshot {
  active: boolean;
  session: SessionStats | null;
  current: CurrentAttempt | null;
  serverTime: number;
}
