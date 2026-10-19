import { Injectable, inject } from '@angular/core';
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
import { Death } from './death.model';
import { sameDeathList } from './death-list';
import { DeathService } from './death.service';

export type DeathsState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ok'; deaths: Death[] };

const POLL_MS = 3000;
// Garde le flux vivant un court instant après la dernière page : en passant d'une page à l'autre,
// on retrouve les données déjà chargées au lieu de repartir sur un écran de chargement.
const KEEP_ALIVE_MS = 10_000;

/**
 * Les morts du compte connecté, tenues à jour pour TOUTES les pages.
 *
 * Avant, seul le tableau de bord interrogeait l'API toutes les 3 s : les autres pages (maps, détail
 * d'une map, profil) chargeaient une fois et restaient figées jusqu'au rechargement du navigateur.
 *
 * - une seule requête toutes les 3 s, quel que soit le nombre de pages qui écoutent (share) ;
 * - rien n'est demandé tant que l'onglet est caché ;
 * - distinctUntilChanged : l'API renvoie une NOUVELLE liste à chaque requête, même identique ;
 *   on ne propage que ce qui change (mort ajoutée, passage en erreur), donc pas de redessin inutile.
 *
 * Le temps réel (SSE) remplacera plus tard cette interrogation régulière.
 */
@Injectable({ providedIn: 'root' })
export class DeathsLive {
  private readonly deaths = inject(DeathService);

  readonly state$: Observable<DeathsState> = timer(0, POLL_MS).pipe(
    filter((tick) => tick === 0 || !document.hidden),
    switchMap(() =>
      this.deaths.getDeaths().pipe(
        map((deaths): DeathsState => ({ status: 'ok', deaths })),
        catchError(() => of<DeathsState>({ status: 'error' })),
      ),
    ),
    startWith<DeathsState>({ status: 'loading' }),
    distinctUntilChanged((a, b) =>
      a.status === 'ok' && b.status === 'ok' ? sameDeathList(a.deaths, b.deaths) : a.status === b.status,
    ),
    share({
      connector: () => new ReplaySubject<DeathsState>(1),
      resetOnRefCountZero: () => timer(KEEP_ALIVE_MS),
    }),
  );
}
