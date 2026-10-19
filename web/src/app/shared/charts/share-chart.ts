import { Component, computed, input } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { themeColors } from '../../core/chart-data';
import { Count } from '../../core/map-stats';

/** Anneau : part de chaque élément dans le total (les plus petits sont regroupés en « Autres »). */
@Component({
  selector: 'app-share-chart',
  imports: [BaseChartDirective],
  template: `
    <div class="box">
      <canvas baseChart type="doughnut" [data]="data()" [options]="options" role="img"
        aria-label="Répartition des morts"></canvas>
    </div>
  `,
  styles: `
    .box { position: relative; height: 240px; }
  `,
})
export class ShareChart {
  readonly items = input.required<Count[]>();
  /** Nombre d'éléments affichés avant le regroupement « Autres ». */
  readonly top = input(5);

  private readonly c = themeColors();

  protected readonly data = computed<ChartConfiguration<'doughnut'>['data']>(() => {
    const sorted = this.items();
    const head = sorted.slice(0, this.top());
    const rest = sorted.slice(this.top()).reduce((sum, i) => sum + i.count, 0);
    const parts = rest > 0 ? [...head, { label: 'Autres', count: rest }] : head;
    const palette = [this.c.accent, this.c.info, this.c.warning, this.c.success, this.c.lavender, this.c.grid];
    return {
      labels: parts.map((p) => p.label.split('/').at(-1) ?? p.label),
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

  protected readonly options: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: {
      legend: { position: 'right', labels: { color: this.c.text, boxWidth: 12, padding: 12 } },
    },
  };
}
