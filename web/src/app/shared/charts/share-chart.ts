import { Component, computed, input } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { themeColors } from '../../core/chart-data';
import { percent } from '../../core/format';
import { Count } from '../../core/map-stats';

/**
 * Anneau : part de chaque élément dans le total (les plus petits sont regroupés en « Autres »).
 * Le canvas est muet pour les lecteurs d'écran : ils lisent à la place un tableau masqué qui
 * reprend les mêmes chiffres (WCAG 1.1.1).
 */
@Component({
  selector: 'app-share-chart',
  imports: [BaseChartDirective],
  template: `
    <div class="box">
      <canvas baseChart type="doughnut" [data]="data()" [options]="options" aria-hidden="true"></canvas>
    </div>
    <table class="ds-sr-only">
      <caption>{{ caption() }}</caption>
      <thead>
        <tr><th scope="col">{{ itemHeader() }}</th><th scope="col">Morts</th><th scope="col">Part</th></tr>
      </thead>
      <tbody>
        @for (p of parts(); track p.label) {
          <tr><th scope="row">{{ shortLabel(p) }}</th><td>{{ p.count }}</td><td>{{ share(p) }}</td></tr>
        }
      </tbody>
    </table>
  `,
  styles: `
    .box { position: relative; height: 240px; }
  `,
})
export class ShareChart {
  readonly items = input.required<Count[]>();
  /** Nombre d'éléments affichés avant le regroupement « Autres ». */
  readonly top = input(5);
  /** Titre du tableau lu par les lecteurs d'écran. */
  readonly caption = input('Répartition des morts');
  /** En-tête de la première colonne de ce tableau. */
  readonly itemHeader = input('Élément');

  private readonly c = themeColors();

  protected readonly parts = computed<Count[]>(() => {
    const sorted = this.items();
    const head = sorted.slice(0, this.top());
    const rest = sorted.slice(this.top()).reduce((sum, i) => sum + i.count, 0);
    return rest > 0 ? [...head, { label: 'Autres', count: rest }] : head;
  });

  private readonly total = computed(() => this.parts().reduce((sum, p) => sum + p.count, 0));

  protected readonly data = computed<ChartConfiguration<'doughnut'>['data']>(() => {
    const parts = this.parts();
    const palette = [this.c.accent, this.c.info, this.c.warning, this.c.success, this.c.lavender, this.c.grid];
    return {
      labels: parts.map((p) => this.shortLabel(p)),
      datasets: [
        {
          data: parts.map((p) => p.count),
          backgroundColor: parts.map((_, i) => palette[i % palette.length]),
          borderColor: this.c.surface,
          borderWidth: 3,
        },
      ],
    };
  });

  protected shortLabel(p: Count): string {
    return p.label.split('/').at(-1) ?? p.label;
  }

  protected share(p: Count): string {
    return this.total() > 0 ? percent(p.count / this.total()) : '0 %';
  }

  protected readonly options: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    // Pas d'animation de redessin : une mort qui arrive met le graphique à jour sans le faire « rejouer ».
    animation: false,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: {
      legend: { position: 'right', labels: { color: this.c.text, boxWidth: 12, padding: 12 } },
    },
  };
}
