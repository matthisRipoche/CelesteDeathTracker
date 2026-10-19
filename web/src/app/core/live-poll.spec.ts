import { HttpErrorResponse } from '@angular/common/http';
import { discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { LiveState, livePoll } from './live-poll';

describe('livePoll', () => {
  it('réémet seulement quand la donnée change', fakeAsync(() => {
    const responses = [{ n: 1 }, { n: 1 }, { n: 2 }];
    let i = 0;
    const states: LiveState<{ n: number }>[] = [];
    const sub = livePoll(() => of(responses[i++])).subscribe((s) => states.push(s));

    tick(0);
    tick(3000);
    tick(3000);

    expect(states.map((s) => s.status)).toEqual(['loading', 'ok', 'ok']); // le 2e « ok » est la donnée n = 2
    expect(states[2]).toEqual({ status: 'ok', data: { n: 2 } });

    sub.unsubscribe();
    discardPeriodicTasks();
  }));

  it("garde le code HTTP de l'erreur (404 = rien à afficher, autre = réessai)", fakeAsync(() => {
    const states: LiveState<string>[] = [];
    const sub = livePoll<string>(() => throwError(() => new HttpErrorResponse({ status: 404 }))).subscribe((s) =>
      states.push(s),
    );

    tick(0);
    tick(3000); // même erreur : pas de réémission

    expect(states).toEqual([{ status: 'loading' }, { status: 'error', code: 404 }]);

    sub.unsubscribe();
    discardPeriodicTasks();
  }));

  it('récupère quand le serveur revient après une erreur', fakeAsync(() => {
    let fail = true;
    const states: LiveState<string>[] = [];
    const sub = livePoll<string>(() =>
      fail ? throwError(() => new HttpErrorResponse({ status: 500 })) : of('ok'),
    ).subscribe((s) => states.push(s));

    tick(0);
    fail = false;
    tick(3000);

    expect(states.map((s) => s.status)).toEqual(['loading', 'error', 'ok']);

    sub.unsubscribe();
    discardPeriodicTasks();
  }));

  it("n'a qu'une requête en cours à la fois (switchMap)", fakeAsync(() => {
    const pending = new Subject<string>();
    let calls = 0;
    const sub = livePoll<string>(() => {
      calls++;
      return pending;
    }).subscribe();

    tick(0);
    tick(3000);

    expect(calls).toBe(2); // une nouvelle requête à chaque tick, la précédente est abandonnée

    sub.unsubscribe();
    discardPeriodicTasks();
  }));
});
