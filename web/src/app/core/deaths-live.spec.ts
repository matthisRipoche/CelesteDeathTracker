import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Death } from './death.model';
import { DeathsLive, DeathsState } from './deaths-live';

const death = (id: number): Death => ({
  id,
  timestamp: 1_000 * id,
  chapter: 'Celeste/1-ForsakenCity',
  room: '1',
  deathX: 0,
  deathY: 0,
  totalDeaths: id,
  sessionDeaths: id,
  timeInRoomMs: 0,
});

describe('DeathsLive', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  });

  it("ne réémet que lorsqu'une mort arrive, pas à chaque rafraîchissement", fakeAsync(() => {
    const http = TestBed.inject(HttpTestingController);
    const states: DeathsState[] = [];
    const sub = TestBed.inject(DeathsLive).state$.subscribe((s) => states.push(s));
    tick(0); // timer(0, …) émet au tour suivant

    http.expectOne('/api/deaths').flush([death(1)]);
    tick(3000);
    http.expectOne('/api/deaths').flush([death(1)]); // nouvelle réponse HTTP, mêmes données
    tick(3000);
    http.expectOne('/api/deaths').flush([death(1)]);
    expect(states.map((s) => s.status)).toEqual(['loading', 'ok']);

    tick(3000);
    http.expectOne('/api/deaths').flush([death(1), death(2)]); // une mort de plus
    expect(states.length).toBe(3);

    sub.unsubscribe();
    tick(10_000);
    discardPeriodicTasks();
  }));

  it('partage une seule requête entre plusieurs pages', fakeAsync(() => {
    const http = TestBed.inject(HttpTestingController);
    const live = TestBed.inject(DeathsLive);
    const a = live.state$.subscribe();
    const b = live.state$.subscribe();
    tick(0);

    http.expectOne('/api/deaths').flush([death(1)]); // une seule requête pour deux abonnés
    tick(3000);
    http.expectOne('/api/deaths').flush([death(1)]);

    a.unsubscribe();
    b.unsubscribe();
    tick(10_000);
    discardPeriodicTasks();
  }));

  it('repasse en erreur puis récupère quand l\'API revient', fakeAsync(() => {
    const http = TestBed.inject(HttpTestingController);
    const states: DeathsState[] = [];
    const sub = TestBed.inject(DeathsLive).state$.subscribe((s) => states.push(s));
    tick(0);

    http.expectOne('/api/deaths').flush('', { status: 500, statusText: 'Erreur' });
    tick(3000);
    http.expectOne('/api/deaths').flush([death(1)]);

    expect(states.map((s) => s.status)).toEqual(['loading', 'error', 'ok']);

    sub.unsubscribe();
    tick(10_000);
    discardPeriodicTasks();
  }));
});
