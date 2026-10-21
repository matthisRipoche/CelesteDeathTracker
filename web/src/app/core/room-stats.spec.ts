import { DeathPoint, RoomOutcome, RoomOutcomeKind } from './face.model';
import { outcomeTrend, scatterLayout } from './room-stats';

const death = (deathId: number, x: number, y: number, first = true): DeathPoint => ({
  deathId,
  attemptId: deathId,
  at: deathId * 1000,
  x,
  y,
  first,
});

const outcomes = (kinds: RoomOutcomeKind[]): RoomOutcome[] =>
  kinds.map((outcome, i) => ({ attemptId: i, startedAt: i * 1000, outcome }));

describe('scatterLayout', () => {
  it("ne dessine rien sans mort", () => {
    expect(scatterLayout([], 400, 200, 20)).toEqual({ dots: [], bounds: null });
  });

  it('garde la même échelle sur X et sur Y et centre le nuage', () => {
    // 100 px de large pour 50 px de haut dans le jeu : le cadre 400 x 200 est exactement rempli (marges comprises)
    const { dots } = scatterLayout([death(1, 1000, 500), death(2, 1100, 550)], 440, 240, 20);
    const [a, b] = dots;
    expect(b.x - a.x).toBeCloseTo(400); // 100 px de jeu -> 400 : échelle 4
    expect(b.y - a.y).toBeCloseTo(200); // 50 px de jeu -> 200 : même échelle
    expect(a.x).toBeCloseTo(20);
    expect((a.y + b.y) / 2).toBeCloseTo(120); // centré verticalement
  });

  it("n'étale pas des morts presque au même endroit sur tout le cadre", () => {
    const { dots, bounds } = scatterLayout([death(1, 5000, 700), death(2, 5004, 702)], 440, 240, 20);
    expect(Math.abs(dots[1].x - dots[0].x)).toBeLessThan(30);
    expect(bounds!.xMax - bounds!.xMin).toBe(48);
  });

  it('place une mort unique au centre', () => {
    const { dots } = scatterLayout([death(1, 6853, -2200)], 400, 200, 20);
    expect(dots[0].x).toBeCloseTo(200);
    expect(dots[0].y).toBeCloseTo(100);
  });

  it("garde l'axe Y du jeu (vers le bas) : plus grand Y = plus bas dans le dessin", () => {
    const { dots } = scatterLayout([death(1, 0, -2223), death(2, 0, -2168)], 400, 200, 20);
    expect(dots[1].y).toBeGreaterThan(dots[0].y);
  });

  it('garde le marquage première mort', () => {
    const { dots } = scatterLayout([death(1, 0, 0, true), death(2, 10, 10, false)], 400, 200, 20);
    expect(dots.map((d) => d.first)).toEqual([true, false]);
  });
});

describe('outcomeTrend', () => {
  it("n'estime rien avec moins de 6 tentatives", () => {
    expect(outcomeTrend(outcomes(['died', 'died', 'cleared', 'cleared', 'cleared']))).toBeNull();
  });

  it('compare le début et la fin', () => {
    const t = outcomeTrend(outcomes(['died', 'died', 'died', 'cleared', 'cleared', 'cleared']))!;
    expect(t.n).toBe(3);
    expect(t.first).toBe(0);
    expect(t.last).toBe(1);
    expect(t.better).toBe(true);
  });

  it('ne compare pas plus de 10 tentatives de chaque côté', () => {
    const many = outcomes(Array.from({ length: 40 }, () => 'died' as const));
    expect(outcomeTrend(many)!.n).toBe(10);
  });

  it("n'annonce pas de progrès quand on régresse ou stagne", () => {
    expect(outcomeTrend(outcomes(['cleared', 'cleared', 'cleared', 'died', 'died', 'died']))!.better).toBe(false);
    expect(outcomeTrend(outcomes(['cleared', 'died', 'cleared', 'cleared', 'died', 'cleared']))!.better).toBe(false);
  });
});
