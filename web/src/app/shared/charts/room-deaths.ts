import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { DeathPoint } from '../../core/face.model';
import { scatterLayout } from '../../core/room-stats';

const W = 1000;
const H = 420;
const PAD = 40;

/**
 * Où l'on meurt dans une room : un point par mort, aux coordonnées du jeu (Y vers le bas, comme à l'écran).
 * Les premières morts, celles qui font perdre le deathless, sont pleines ; les suivantes sont creuses.
 * Dessiné en SVG pour suivre le thème clair ou sombre sans code. Le cadre vient des morts elles-mêmes :
 * le jeu n'envoie ni la taille ni la position de la room.
 */
@Component({
  selector: 'app-room-deaths',
  imports: [DatePipe],
  template: `
    <div class="scroll">
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="label()">
        <rect x="1" y="1" [attr.width]="W - 2" [attr.height]="H - 2" class="frame" />
        @if (layout().bounds; as b) {
          <text [attr.x]="PAD" y="22" class="axis">X {{ b.xMin }} → {{ b.xMax }}</text>
          <text [attr.x]="W - PAD" [attr.y]="H - 12" text-anchor="end" class="axis">Y {{ b.yMin }} ↓ {{ b.yMax }}</text>
        }
        @for (d of layout().dots; track d.id) {
          @if (d.first) {
            <circle [attr.cx]="d.x" [attr.cy]="d.y" r="9" class="first"><title>Première mort, {{ d.at | date: 'dd/MM HH:mm' }}</title></circle>
          } @else {
            <circle [attr.cx]="d.x" [attr.cy]="d.y" r="7" class="next"><title>Mort suivante, {{ d.at | date: 'dd/MM HH:mm' }}</title></circle>
          }
        }
      </svg>
    </div>
    <p class="legend">
      <span class="key"><svg width="18" height="18" aria-hidden="true"><circle cx="9" cy="9" r="7" class="first" /></svg> Première mort de la tentative</span>
      <span class="key"><svg width="18" height="18" aria-hidden="true"><circle cx="9" cy="9" r="6" class="next" /></svg> Mort suivante</span>
    </p>
  `,
  styles: `
    .scroll { overflow-x: auto; }
    svg { display: block; width: 100%; min-width: 560px; height: auto; }
    .legend svg { display: inline-block; width: 18px; min-width: 0; vertical-align: middle; }
    .frame { fill: var(--color-surface-sunken); stroke: var(--color-border); stroke-width: 1; }
    .axis { fill: var(--color-text-muted); font: 14px var(--font-sans); }
    .first { fill: var(--color-danger); stroke: var(--color-bg); stroke-width: 2; }
    .next { fill: none; stroke: var(--color-danger); stroke-width: 2.5; }
    .legend { display: flex; gap: var(--space-5); flex-wrap: wrap; margin: var(--space-2) 0 0; font-size: var(--text-sm); color: var(--color-text-muted); }
    .key { display: inline-flex; align-items: center; gap: var(--space-2); }
  `,
})
export class RoomDeaths {
  readonly points = input.required<DeathPoint[]>();

  protected readonly W = W;
  protected readonly H = H;
  protected readonly PAD = PAD;

  protected readonly layout = computed(() => scatterLayout(this.points(), W, H, PAD));

  protected readonly label = computed(() => {
    const n = this.points().length;
    const first = this.points().filter((p) => p.first).length;
    return `Positions de ${n} mort${n > 1 ? 's' : ''} dans la room, dont ${first} première${first > 1 ? 's' : ''} de leur tentative`;
  });
}
