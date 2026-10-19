import { Death } from './death.model';

export interface DayPoint {
  /** Jour local au format AAAA-MM-JJ. */
  key: string;
  /** Libellé court affiché sur l'axe : « 16/10 ». */
  label: string;
  count: number;
}

function dayKey(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/**
 * Morts par jour sur les `days` derniers jours (aujourd'hui inclus), jours vides compris :
 * un graphique sans trous se lit mieux qu'une liste de jours « actifs » seulement.
 */
export function deathsPerDay(deaths: Death[], days: number): DayPoint[] {
  const counts = new Map<string, number>();
  for (const d of deaths) {
    const k = dayKey(new Date(d.timestamp));
    counts.set(k, (counts.get(k) ?? 0) + 1);
  }
  const points: DayPoint[] = [];
  const today = new Date();
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i);
    const key = dayKey(date);
    const label = `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}`;
    points.push({ key, label, count: counts.get(key) ?? 0 });
  }
  return points;
}

/** Nombre de morts d'aujourd'hui (jour local). */
export function deathsToday(deaths: Death[]): number {
  const today = dayKey(new Date());
  return deaths.filter((d) => dayKey(new Date(d.timestamp)) === today).length;
}

/** Couleurs lues dans les jetons CSS : Chart.js dessine en canvas et ne comprend pas var(--…). */
export function themeColors() {
  const css = getComputedStyle(document.documentElement);
  const get = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
  return {
    accent: get('--ds-pink-400', '#f8588c'),
    info: get('--ds-ice-400', '#66c4e1'),
    warning: get('--ds-gold-400', '#f2c230'),
    success: get('--ds-green-400', '#93bd40'),
    danger: get('--ds-strawberry-500', '#ed4a44'),
    lavender: get('--ds-lavender-500', '#8479c5'),
    grid: get('--ds-night-600', '#3b3470'),
    text: get('--ds-lavender-400', '#9d97d1'),
    surface: get('--ds-night-900', '#151229'),
  };
}
