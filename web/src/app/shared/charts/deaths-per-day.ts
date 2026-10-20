import { Component, computed, input } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { deathsPerDay, themeColors } from '../../core/chart-data';
import { Death } from '../../core/death.model';

/**
 * Histogramme des morts par jour. Le jour le plus meurtrier est mis en évidence.
 * Le canvas est muet pour les lecteurs d'écran : ils lisent à la place un tableau masqué qui
 * reprend les mêmes chiffres (WCAG 1.1.1).
 */
@Component({
  selector: 'app-deaths-per-day',
  imports: [BaseChartDirective],
  template: `
    <div class="box">
      <canvas
        baseChart
        type="bar"
        [data]="data()"
        [options]="options"
        aria-hidden="true"
      ></canvas>
    </div>
    <table class="ds-sr-only">
      <caption>Morts par jour sur les {{ days() }} derniers jours</caption>
      <thead>
        <tr><th scope="col">Jour</th><th scope="col">Morts</th></tr>
      </thead>
      <tbody>
        @for (p of points(); track p.key) {
          <tr>
            <th scope="row">{{ p.label }}</th>
            <td>{{ p.count }}@if (max() > 0 && p.count === max()) { (jour le plus meurtrier)}</td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styles: `
    .box { position: relative; height: 240px; }
  `,
})
export class DeathsPerDay {
  readonly deaths = input.required<Death[]>();
  readonly days = input(14);

  private readonly c = themeColors();

  protected readonly points = computed(() => deathsPerDay(this.deaths(), this.days()));
  protected readonly max = computed(() => Math.max(...this.points().map((p) => p.count), 0));

  protected readonly data = computed<ChartConfiguration<'bar'>['data']>(() => {
    const points = this.points();
    const max = this.max();
    return {
      labels: points.map((p) => p.label),
      datasets: [
        {
          label: 'Morts',
          data: points.map((p) => p.count),
          backgroundColor: points.map((p) => (max > 0 && p.count === max ? this.c.danger : this.c.accent)),
          borderRadius: 2,
          maxBarThickness: 36,
        },
      ],
    };
  });

  protected readonly options: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    // Pas d'animation de redessin : une mort qui arrive met le graphique à jour sans le faire « rejouer ».
    animation: false,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { display: false }, ticks: { color: this.c.text } },
      y: {
        beginAtZero: true,
        grid: { color: this.c.grid },
        ticks: { color: this.c.text, precision: 0 },
      },
    },
  };
}
