/** Durée en millisecondes -> « m:ss » (« 1:23 »). Pas de durée connue -> « — ». */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null || ms < 0) return '—';
  const total = Math.round(ms / 1000);
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/** Chronomètre : « 1:42 » sous une heure, « 1:05:09 » au-delà. Négatif ou inconnu -> « 0:00 ». */
export function formatClock(ms: number | null | undefined): string {
  const total = Math.max(0, Math.floor((ms ?? 0) / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${ss}` : `${minutes}:${ss}`;
}

/** Nom lisible d'un SID quand le jeu n'a pas envoyé le nom officiel : « Celeste/1-ForsakenCity » -> « 1-ForsakenCity ». */
export function shortMapName(sid: string): string {
  return sid.split('/').at(-1) ?? sid;
}

/** Jeu de cartes d'un SID : « monikadsidespack/0/7-Summit » -> « monikadsidespack ». */
export function modOf(sid: string): string {
  return sid.split('/')[0];
}

/** Pourcentage entier : 0.25 -> « 25 % ». */
export function percent(ratio: number): string {
  return `${Math.round(ratio * 100)} %`;
}
