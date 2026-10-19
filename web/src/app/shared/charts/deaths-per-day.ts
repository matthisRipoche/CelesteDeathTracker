import { Component, computed, input } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { deathsPerDay, themeColors } from '../../core/chart-data';
import { Death } from '../../core/death.model';

/** Histogramme des morts par jour. Le jour le plus meurtrier est mis en évidence. */
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
        role="img"
        [attr.aria-label]="'Morts par jour sur les ' + days() + ' derniers jours'"
      ></canvas>
    </div>
  `,
  styles: `
    .box { position: relative; height: 240px; }
  `,
})
export class DeathsPerDay {
  readonly deaths = input.required<Death[]>();
  readonly days = input(14);

  private readonly c = themeColors();

  protected readonly data = computed<ChartConfiguration<'bar'>['data']>(() => {
    const points = deathsPerDay(this.deaths(), this.days());
    const max = Math.max(...points.map((p) => p.count), 0);
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
