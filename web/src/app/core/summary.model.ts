import { Side } from './face.model';

/** Miroirs de GET /api/summary (cf. SummaryDtos.java). */

/**
 * Une face avec sa progression. bestRooms = rooms atteintes par la tentative allée le plus loin ;
 * knownRooms = rooms vues sur toutes les tentatives (le jeu n'envoie pas le nombre de rooms d'une map).
 */
export interface FaceProgress {
  sid: string;
  side: Side;
  mapName: string | null;
  lastPlayedAt: number;
  attempts: number;
  clears: number;
  bestRooms: number;
  knownRooms: number;
}

export interface MostPlayed {
  sid: string;
  side: Side;
  mapName: string | null;
  attempts: number;
}

/** bestAttemptMs = la tentative du jour qui a tenu le plus longtemps. */
export interface Today {
  attempts: number;
  clears: number;
  bestAttemptMs: number | null;
  mostPlayed: MostPlayed | null;
}

/** day au format AAAA-MM-JJ, dans le fuseau du navigateur. */
export interface ActivityDay {
  day: string;
  attempts: number;
  clears: number;
}

/** resume = dernière face jouée ; recent = les faces jouées juste avant (3 au plus) ; activity = 14 jours. */
export interface Summary {
  resume: FaceProgress | null;
  today: Today;
  recent: FaceProgress[];
  activity: ActivityDay[];
}
