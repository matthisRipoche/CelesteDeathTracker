import { Death } from './death.model';

export interface Count {
  label: string;
  count: number;
}

/** Nom lisible d'un SID : "monikadsidespack/0/7-Summit" -> "7-Summit". */
export function mapName(sid: string): string {
  return sid.split('/').at(-1) ?? sid;
}

/** Jeu de cartes (levelSet) d'un SID : "monikadsidespack/0/7-Summit" -> "monikadsidespack". */
export function modName(sid: string): string {
  return sid.split('/')[0];
}

/** Compte les morts par clé, trié du plus meurtrier au moins meurtrier. */
export function countBy(deaths: Death[], key: (d: Death) => string): Count[] {
  const counts = new Map<string, number>();
  for (const d of deaths) {
    const k = key(d);
    counts.set(k, (counts.get(k) ?? 0) + 1);
  }
  return [...counts]
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count);
}
