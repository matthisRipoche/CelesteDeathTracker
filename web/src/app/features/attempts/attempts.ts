import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { AttemptPage, Period } from '../../core/attempt.model';
import { AttemptService, PAGE_SIZE } from '../../core/attempt.service';
import { AttemptResult, Side } from '../../core/face.model';
import { formatDuration, shortMapName } from '../../core/format';
import { LiveState } from '../../core/live-poll';
import { PxBerry, PxBurst } from '../../shared/pixel/pixel-icons';
import { Shell } from '../../shared/shell/shell';

const RESULT_LABEL: Record<AttemptResult, string> = {
  success: 'Réussie',
  death: 'Morte',
  abandoned: 'Abandonnée',
  ongoing: 'En cours',
};

/**
 * Historique : toutes les tentatives significatives, de la plus récente à la plus ancienne, avec des
 * filtres (résultat, période) et, si on vient d'une face, un filtre sur cette face (?sid=…&side=…).
 * Pas d'interrogation régulière : c'est un historique, la page se recharge quand un filtre change.
 */
@Component({
  selector: 'app-attempts',
  imports: [DatePipe, RouterLink, Shell, PxBerry, PxBurst],
  templateUrl: './attempts.html',
  styleUrl: './attempts.css',
})
export class Attempts {
  private readonly attempts = inject(AttemptService);
  private readonly route = inject(ActivatedRoute);

  protected readonly pageSize = PAGE_SIZE;

  protected readonly result = signal<AttemptResult | null>(null);
  protected readonly period = signal<Period>('all');
  protected readonly page = signal(0);

  /** La face venant de l'adresse (?sid=&side=) : un lien depuis la page d'une face. */
  protected readonly face = toSignal(
    this.route.queryParamMap.pipe(
      map((p) => {
        const side = p.get('side')?.toUpperCase();
        return {
          sid: p.get('sid'),
          side: side === 'A' || side === 'B' || side === 'C' ? (side as Side) : null,
        };
      }),
    ),
    { requireSync: true },
  );

  private readonly filter = computed(() => ({
    ...this.face(),
    result: this.result(),
    period: this.period(),
    page: this.page(),
  }));

  protected readonly state = toSignal(
    toObservable(this.filter).pipe(
      switchMap((f) =>
        this.attempts.list(f).pipe(
          map((data): LiveState<AttemptPage> => ({ status: 'ok', data })),
          catchError((e: unknown) =>
            of<LiveState<AttemptPage>>({ status: 'error', code: e instanceof HttpErrorResponse ? e.status : 0 }),
          ),
          startWith<LiveState<AttemptPage>>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as LiveState<AttemptPage> },
  );

  protected readonly data = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data : null;
  });

  protected readonly pageCount = computed(() => Math.max(1, Math.ceil((this.data()?.total ?? 0) / PAGE_SIZE)));
  protected readonly filtered = computed(
    () => this.result() !== null || this.period() !== 'all' || this.face().sid !== null || this.face().side !== null,
  );

  protected setResult(value: string): void {
    this.result.set(value === '' ? null : (value as AttemptResult));
    this.page.set(0);
  }

  protected setPeriod(value: string): void {
    this.period.set(value as Period);
    this.page.set(0);
  }

  protected previous(): void {
    this.page.update((p) => Math.max(0, p - 1));
  }

  protected next(): void {
    this.page.update((p) => Math.min(this.pageCount() - 1, p + 1));
  }

  protected readonly fmt = formatDuration;
  protected readonly label = (r: AttemptResult) => RESULT_LABEL[r];
  protected readonly title = (a: { sid: string; mapName: string | null }) => a.mapName ?? shortMapName(a.sid);
}
