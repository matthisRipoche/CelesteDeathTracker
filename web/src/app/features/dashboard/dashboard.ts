import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Shell } from '../../shared/shell/shell';
import { Death } from '../../core/death.model';
import { DeathsLive } from '../../core/deaths-live';
import { deathsToday } from '../../core/chart-data';
import { DeathsPerDay } from '../../shared/charts/deaths-per-day';
import { ShareChart } from '../../shared/charts/share-chart';
import { Sprite } from '../../shared/sprite/sprite';

interface Count {
  label: string;
  count: number;
}

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, DecimalPipe, RouterLink, Shell, DeathsPerDay, ShareChart, Sprite],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  /**
   * Les morts viennent d'un flux PARTAGÉ par toutes les pages (voir core/deaths-live.ts) : interrogé
   * toutes les 3 s, sans rien redessiner quand rien n'a changé. toSignal() le convertit en signal
   * lisible dans le template : state().
   */
  protected readonly state = toSignal(inject(DeathsLive).state$, { requireSync: true });

  // computed() = valeur dérivée, recalculée uniquement quand state() change.
  protected readonly deaths = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.deaths : [];
  });

  protected readonly total = computed(() => this.deaths().length);
  protected readonly today = computed(() => deathsToday(this.deaths()));

  private readonly rooms = computed(() =>
    this.countBy((d) => `${this.mapName(d.chapter)} / ${d.room}`),
  );

  protected readonly roomCount = computed(() => this.rooms().length);
  protected readonly topRooms = computed(() => this.rooms().slice(0, 8));

  protected readonly byChapter = computed(() => this.countBy((d) => d.chapter));

  protected readonly recent = computed(() => [...this.deaths()].reverse().slice(0, 15));

  protected readonly maxRoom = computed(() => this.topRooms()[0]?.count ?? 1);
  protected readonly maxChapter = computed(() => this.byChapter()[0]?.count ?? 1);

  protected mapName(chapter: string): string {
    return chapter.split('/').at(-1) ?? chapter;
  }

  protected modName(chapter: string): string {
    return chapter.split('/')[0];
  }

  private countBy(key: (d: Death) => string): Count[] {
    const counts = new Map<string, number>();
    for (const d of this.deaths()) {
      const k = key(d);
      counts.set(k, (counts.get(k) ?? 0) + 1);
    }
    return [...counts]
      .map(([label, count]) => ({ label, count }))
      .sort((a, b) => b.count - a.count);
  }
}
