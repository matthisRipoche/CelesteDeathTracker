import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { formatDuration, modOf, shortMapName } from '../../core/format';
import { LiveState } from '../../core/live-poll';
import { FaceProgress, Summary } from '../../core/summary.model';
import { SummaryService } from '../../core/summary.service';
import { ActivityChart } from '../../shared/charts/activity-chart';
import { PxBerry } from '../../shared/pixel/pixel-icons';
import { Shell } from '../../shared/shell/shell';

/**
 * Tableau de bord : « où en suis-je aujourd'hui ? ». Reprendre la dernière face, chiffres du jour, maps
 * récentes et activité sur 14 jours, tout calculé par GET /api/summary. Chargé une fois à l'ouverture :
 * la partie en cours se suit sur /live (et dans le bandeau de la coque).
 */
@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, RouterLink, Shell, ActivityChart, PxBerry],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  protected readonly state = toSignal(
    inject(SummaryService).summary().pipe(
      map((data): LiveState<Summary> => ({ status: 'ok', data })),
      catchError((e: unknown) =>
        of<LiveState<Summary>>({ status: 'error', code: e instanceof HttpErrorResponse ? e.status : 0 }),
      ),
      startWith<LiveState<Summary>>({ status: 'loading' }),
    ),
    { requireSync: true },
  );

  protected readonly summary = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data : null;
  });

  /** Tentatives sur les 14 jours affichés. */
  protected readonly fortnight = computed(() =>
    (this.summary()?.activity ?? []).reduce((n, d) => n + d.attempts, 0),
  );

  protected readonly title = (f: { sid: string; mapName: string | null }) => f.mapName ?? shortMapName(f.sid);
  protected readonly mod = modOf;
  protected readonly fmt = formatDuration;

  /** Part des rooms connues atteinte par la meilleure tentative, en %. */
  protected progress(f: FaceProgress): number {
    return f.knownRooms > 0 ? (f.bestRooms / f.knownRooms) * 100 : 0;
  }

  protected faceLink(f: { side: string }): string[] {
    return ['/maps', f.side.toLowerCase()];
  }
}
