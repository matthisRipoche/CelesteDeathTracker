import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { countBy, mapName, modName } from '../../core/map-stats';
import { Death } from '../../core/death.model';
import { DeathService } from '../../core/death.service';
import { Shell } from '../../shared/shell/shell';
import { DeathsPerDay } from '../../shared/charts/deaths-per-day';
import { PositionsChart } from '../../shared/charts/positions-chart';

type State = { status: 'loading' } | { status: 'error' } | { status: 'ok'; deaths: Death[] };

/** Détail d'une map : le SID vient du paramètre de requête `sid` (il contient des « / »). */
@Component({
  selector: 'app-map-detail',
  imports: [DatePipe, DecimalPipe, RouterLink, Shell, DeathsPerDay, PositionsChart],
  templateUrl: './map-detail.html',
  styleUrl: './map-detail.css',
})
export class MapDetail {
  private readonly deathService = inject(DeathService);

  protected readonly sid = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(map((p) => p.get('sid') ?? '')),
    { requireSync: true },
  );

  private readonly all = toSignal(
    this.deathService.getDeaths().pipe(
      map((deaths): State => ({ status: 'ok', deaths })),
      catchError(() => of<State>({ status: 'error' })),
      startWith<State>({ status: 'loading' }),
    ),
    { requireSync: true },
  );

  protected readonly status = computed(() => this.all().status);

  protected readonly deaths = computed(() => {
    const s = this.all();
    return s.status === 'ok' ? s.deaths.filter((d) => d.chapter === this.sid()) : [];
  });

  protected readonly rooms = computed(() => countBy(this.deaths(), (d) => d.room));
  protected readonly topRooms = computed(() => this.rooms().slice(0, 10));
  protected readonly maxRoom = computed(() => this.rooms()[0]?.count ?? 1);
  protected readonly recent = computed(() => [...this.deaths()].reverse().slice(0, 15));

  /** Room choisie pour le nuage de points ; par défaut la plus meurtrière. */
  private readonly picked = signal<string | null>(null);
  protected readonly selectedRoom = computed(() => {
    const p = this.picked();
    return p && this.rooms().some((r) => r.label === p) ? p : (this.rooms()[0]?.label ?? null);
  });
  protected readonly roomDeaths = computed(() =>
    this.deaths().filter((d) => d.room === this.selectedRoom()),
  );

  protected pick(room: string): void {
    this.picked.set(room);
  }

  protected readonly mapName = mapName;
  protected readonly modName = modName;
}
