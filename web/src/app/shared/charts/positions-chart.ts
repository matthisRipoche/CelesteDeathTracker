import { Component, computed, input } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { themeColors } from '../../core/chart-data';
import { Death } from '../../core/death.model';

/**
 * Nuage de points des endroits où l'on meurt dans une room (deathX, deathY).
 * L'axe Y est inversé : dans Celeste, y croît vers le bas de l'écran.
 */
@Component({
  selector: 'app-positions-chart',
  imports: [BaseChartDirective],
  template: `
    <div class="box">
      <canvas baseChart type="scatter" [data]="data()" [options]="options" role="img"
        aria-label="Positions des morts dans la room"></canvas>
    </div>
  `,
  styles: `
    .box { position: relative; height: 320px; }
  `,
})
export class PositionsChart {
  readonly deaths = input.required<Death[]>();

  private readonly c = themeColors();

  protected readonly data = computed<ChartConfiguration<'scatter'>['data']>(() => ({
    datasets: [
      {
        label: 'Morts',
        data: this.deaths().map((d) => ({ x: d.deathX, y: d.deathY })),
        backgroundColor: this.c.accent + 'aa',
        borderColor: this.c.accent,
        pointRadius: 5,
        pointHoverRadius: 7,
      },
    ],
  }));

  protected readonly options: ChartConfiguration<'scatter'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { color: this.c.grid }, ticks: { color: this.c.text }, title: { display: true, text: 'X', color: this.c.text } },
      y: {
        reverse: true,
        grid: { color: this.c.grid },
        ticks: { color: this.c.text },
        title: { display: true, text: 'Y', color: this.c.text },
      },
    },
  };
}
