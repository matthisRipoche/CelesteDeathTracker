import { Component, computed, input } from '@angular/core';
import { EvolutionPoint } from '../../core/face.model';
import { formatDuration } from '../../core/format';

const W = 1000;
const H = 270;
const X0 = 64;
const X1 = 980;
const Y0 = 230;
const Y1 = 20;

/**
 * Évolution d'une face : la durée de chaque tentative, de la plus ancienne à la plus récente.
 * Un trait d'encre relie les tentatives ; les réussites sont des triangles verts.
 * Dessiné en SVG (et non en canvas) pour suivre le thème clair ou sombre sans code.
 */
@Component({
  selector: 'app-face-evolution',
  template: `
    <div class="scroll">
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="label()">
        @for (g of grid(); track g.label) {
          <line [attr.x1]="X0" [attr.x2]="X1" [attr.y1]="g.y" [attr.y2]="g.y" class="grid" />
          <text [attr.x]="X0 - 8" [attr.y]="g.y + 5" text-anchor="end" class="axis">{{ g.label }}</text>
        }
        <polyline [attr.points]="line()" class="line" fill="none" />
        @for (d of dots(); track d.id) {
          <rect [attr.x]="d.x - 3.5" [attr.y]="d.y - 3.5" width="7" height="7" class="dot" />
        }
        @for (w of wins(); track w.id) {
          <path [attr.d]="w.d" class="win" />
        }
        <text [attr.x]="X0" y="262" class="axis">{{ firstLabel() }}</text>
        <text [attr.x]="X1" y="262" text-anchor="end" class="axis">aujourd'hui</text>
      </svg>
    </div>
  `,
  styles: `
    .scroll { overflow-x: auto; }
    svg { display: block; width: 100%; min-width: 640px; height: auto; }
    .grid { stroke: var(--color-border); stroke-width: 1; }
    .axis { fill: var(--color-text-muted); font: 14px var(--font-sans); }
    .line { stroke: var(--color-text); stroke-width: 2; stroke-linejoin: round; }
    .dot { fill: var(--color-text); }
    .win { fill: var(--color-success); stroke: var(--color-bg); stroke-width: 2; }
  `,
})
export class FaceEvolution {
  readonly points = input.required<EvolutionPoint[]>();

  protected readonly W = W;
  protected readonly H = H;
  protected readonly X0 = X0;
  protected readonly X1 = X1;

  /** Seules les tentatives dont la durée est connue sont tracées. */
  private readonly known = computed(() => this.points().filter((p) => p.durationMs != null));

  /** Plafond de l'axe : la plus longue durée, arrondie au multiple de 90 s supérieur (axe en tiers : 0, 30 s, 1 min, 1:30). */
  private readonly max = computed(() => {
    const longest = Math.max(...this.known().map((p) => p.durationMs ?? 0), 1000);
    return Math.ceil(longest / 90_000) * 90_000;
  });

  private readonly coords = computed(() => {
    const pts = this.known();
    const n = pts.length;
    return pts.map((p, i) => ({
      id: p.attemptId,
      result: p.result,
      x: n === 1 ? (X0 + X1) / 2 : X0 + (i * (X1 - X0)) / (n - 1),
      y: Y0 - ((p.durationMs ?? 0) / this.max()) * (Y0 - Y1),
    }));
  });

  protected readonly line = computed(() => this.coords().map((c) => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' '));
  protected readonly dots = computed(() => this.coords().filter((c) => c.result !== 'success'));
  protected readonly wins = computed(() =>
    this.coords()
      .filter((c) => c.result === 'success')
      .map((c) => ({
        id: c.id,
        d: `M${c.x.toFixed(1)} ${(c.y - 11).toFixed(1)} L${(c.x + 10).toFixed(1)} ${(c.y + 8).toFixed(1)} L${(c.x - 10).toFixed(1)} ${(c.y + 8).toFixed(1)} Z`,
      })),
  );

  protected readonly grid = computed(() => {
    const max = this.max();
    return [0, 1, 2, 3].map((i) => {
      const v = (max * i) / 3;
      return { y: Y0 - (v / max) * (Y0 - Y1), label: formatDuration(v) };
    });
  });

  protected readonly firstLabel = computed(() => {
    const first = this.known()[0];
    if (!first) return '';
    const days = Math.floor((Date.now() - first.startedAt) / 86_400_000);
    return days <= 0 ? "aujourd'hui" : days === 1 ? 'hier' : `il y a ${days} jours`;
  });

  protected readonly label = computed(() => {
    const n = this.known().length;
    const wins = this.wins().length;
    return `Durée des ${n} tentatives${wins ? `, dont ${wins} réussie${wins > 1 ? 's' : ''}` : ''}`;
  });
}
