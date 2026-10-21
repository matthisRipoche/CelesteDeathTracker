import { DeathPoint, RoomOutcome } from './face.model';

/** Une mort placée dans le dessin : x et y en unités du dessin, pas du jeu. */
export interface PlacedDeath {
  id: number;
  first: boolean;
  at: number;
  x: number;
  y: number;
}

export interface ScatterLayout {
  dots: PlacedDeath[];
  /** Bornes des coordonnées du jeu affichées (cadre élargi quand les morts sont presque au même endroit). */
  bounds: { xMin: number; xMax: number; yMin: number; yMax: number } | null;
}

/** Écart minimal affiché : quelques morts au même endroit ne doivent pas s'étaler sur tout le cadre. */
const MIN_SPAN = 48;

/**
 * Place les morts dans un cadre de `width` x `height`. L'échelle est la même sur X et sur Y : le jeu est
 * dessiné en pixels carrés, un passage doit garder sa forme. Le nuage est centré dans le cadre.
 * Le jeu n'envoie ni la taille ni la position de la room : le cadre vient des morts elles-mêmes.
 */
export function scatterLayout(points: DeathPoint[], width: number, height: number, pad: number): ScatterLayout {
  if (points.length === 0) return { dots: [], bounds: null };

  const xs = points.map((p) => p.x);
  const ys = points.map((p) => p.y);
  const spanX = Math.max(Math.max(...xs) - Math.min(...xs), MIN_SPAN);
  const spanY = Math.max(Math.max(...ys) - Math.min(...ys), MIN_SPAN);
  const centerX = (Math.max(...xs) + Math.min(...xs)) / 2;
  const centerY = (Math.max(...ys) + Math.min(...ys)) / 2;
  const scale = Math.min((width - 2 * pad) / spanX, (height - 2 * pad) / spanY);

  return {
    dots: points.map((p) => ({
      id: p.deathId,
      first: p.first,
      at: p.at,
      x: width / 2 + (p.x - centerX) * scale,
      y: height / 2 + (p.y - centerY) * scale,
    })),
    bounds: {
      xMin: Math.round(centerX - spanX / 2),
      xMax: Math.round(centerX + spanX / 2),
      yMin: Math.round(centerY - spanY / 2),
      yMax: Math.round(centerY + spanY / 2),
    },
  };
}

export interface OutcomeTrend {
  /** Nombre de tentatives comparées de chaque côté. */
  n: number;
  /** Part de tentatives qui franchissent la room, au début puis récemment (0 à 1). */
  first: number;
  last: number;
  better: boolean;
}

/**
 * Compare la part de tentatives qui franchissent la room au début et récemment. Il en faut au moins 6
 * pour que la comparaison ait un sens ; on compare au plus 10 de chaque côté.
 */
export function outcomeTrend(outcomes: RoomOutcome[]): OutcomeTrend | null {
  if (outcomes.length < 6) return null;
  const n = Math.min(10, Math.floor(outcomes.length / 2));
  const share = (xs: RoomOutcome[]) => xs.filter((o) => o.outcome === 'cleared').length / xs.length;
  const first = share(outcomes.slice(0, n));
  const last = share(outcomes.slice(-n));
  return { n, first, last, better: last > first };
}
