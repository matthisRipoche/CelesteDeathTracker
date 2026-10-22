import { Component, computed, input } from '@angular/core';
import { ActivityDay } from '../../core/summary.model';

/**
 * Tentatives par jour : une barre par jour, la part des deathless réussis en vert au pied de la barre.
 * Dessiné en HTML/CSS (pas Chart.js) : 14 barres n'ont pas besoin d'un canvas, et les jetons du thème
 * s'appliquent directement. Chaque barre porte son texte pour les lecteurs d'écran.
 */
@Component({
  selector: 'app-activity-chart',
  template: `
    <ol class="days">
      @for (d of bars(); track d.day) {
        <li class="day" [class.today]="d.today" [attr.aria-label]="d.text">
          <span class="value" aria-hidden="true">{{ d.attempts || '' }}</span>
          <span class="track" aria-hidden="true">
            <span class="bar" [style.height.%]="d.height">
              <span class="win" [style.height.%]="d.winShare"></span>
            </span>
          </span>
          <span class="label" aria-hidden="true">{{ d.label }}</span>
        </li>
      }
    </ol>
  `,
  styles: `
    :host { display: block; }
    .days {
      list-style: none; margin: 0; padding: 0;
      display: grid; grid-template-columns: repeat(var(--n, 14), minmax(0, 1fr)); gap: 0 var(--space-1);
    }
    .day { display: grid; grid-template-rows: auto 160px auto; justify-items: center; gap: var(--space-1); }
    .value { font-size: var(--text-sm); font-weight: 700; font-variant-numeric: tabular-nums; min-height: 1.2em; }
    .track { display: flex; align-items: flex-end; width: 100%; height: 160px; border-bottom: var(--border-width) solid var(--color-border-strong); }
    .bar { display: flex; flex-direction: column; justify-content: flex-end; width: 100%; max-width: 32px; margin: 0 auto; background: var(--color-text); }
    .win { display: block; background: var(--color-success); }
    .label { font-size: var(--text-xs); color: var(--color-text-muted); font-variant-numeric: tabular-nums; }
    .today .label { color: var(--color-text); font-weight: 700; }
    @media (max-width: 560px) {
      .day:nth-child(odd):not(.today) .label { visibility: hidden; }
    }
  `,
  host: { '[style.--n]': 'days().length' },
})
export class ActivityChart {
  readonly days = input.required<ActivityDay[]>();

  protected readonly bars = computed(() => {
    const days = this.days();
    const max = Math.max(1, ...days.map((d) => d.attempts));
    return days.map((d, i) => {
      const [, month, day] = d.day.split('-');
      const today = i === days.length - 1;
      const tries = `${d.attempts} tentative${d.attempts > 1 ? 's' : ''}`;
      const wins = d.clears > 0 ? `, dont ${d.clears} deathless réussi${d.clears > 1 ? 's' : ''}` : '';
      return {
        day: d.day,
        today,
        attempts: d.attempts,
        label: today ? 'auj.' : `${day}/${month}`,
        height: (d.attempts / max) * 100,
        winShare: d.attempts > 0 ? (d.clears / d.attempts) * 100 : 0,
        text: `${today ? "Aujourd'hui" : `${day}/${month}`} : ${tries}${wins}`,
      };
    });
  });
}
