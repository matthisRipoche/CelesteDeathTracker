/** Une mort telle que renvoyée par l'API (cf. docs/data-model.md). */
export interface Death {
  id: number;
  timestamp: number; // Unix ms (UTC)
  chapter: string;   // SID complet, ex: "monikadsidespack/0/7-Summit"
  room: string;
  deathX: number;
  deathY: number;
  totalDeaths: number;
  sessionDeaths: number;
  timeInRoomMs: number;
}
