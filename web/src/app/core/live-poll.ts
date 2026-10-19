import { HttpErrorResponse } from '@angular/common/http';
import {
  Observable,
  ReplaySubject,
  catchError,
  distinctUntilChanged,
  filter,
  map,
  of,
  share,
  startWith,
  switchMap,
  timer,
} from 'rxjs';

/** État d'une donnée tenue à jour : en chargement, en erreur (avec le code HTTP), ou disponible. */
export type LiveState<T> =
  | { status: 'loading' }
  | { status: 'error'; code: number }
  | { status: 'ok'; data: T };

const POLL_MS = 3000;

/**
 * Interroge `fetch` toutes les 3 s tant qu'au moins une page écoute, sans rien demander quand l'onglet
 * est caché, et ne réémet que si la donnée a vraiment changé (l'API renvoie un nouvel objet à chaque
 * requête, même identique : sans ce filtre, tout serait redessiné toutes les 3 s).
 * Une erreur n'arrête pas le flux : la requête suivante réessaie.
 * Le temps réel (SSE) remplacera plus tard cette interrogation régulière.
 */
export function livePoll<T>(
  fetch: () => Observable<T>,
  same: (a: T, b: T) => boolean = (a, b) => JSON.stringify(a) === JSON.stringify(b),
): Observable<LiveState<T>> {
  return timer(0, POLL_MS).pipe(
    filter((tick) => tick === 0 || !document.hidden),
    switchMap(() =>
      fetch().pipe(
        map((data): LiveState<T> => ({ status: 'ok', data })),
        catchError((e: unknown) =>
          of<LiveState<T>>({ status: 'error', code: e instanceof HttpErrorResponse ? e.status : 0 }),
        ),
      ),
    ),
    startWith<LiveState<T>>({ status: 'loading' }),
    distinctUntilChanged((a, b) => {
      if (a.status === 'ok' && b.status === 'ok') return same(a.data, b.data);
      if (a.status === 'error' && b.status === 'error') return a.code === b.code;
      return a.status === b.status;
    }),
    share({ connector: () => new ReplaySubject<LiveState<T>>(1), resetOnRefCountZero: true }),
  );
}
