import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AttemptDetail } from '../../core/attempt.model';
import { AttemptDetailPage } from './attempt-detail';

const SID = 'Celeste/1-ForsakenCity';

const attempt = (over: Partial<AttemptDetail> = {}): AttemptDetail => ({
  id: 7,
  startedAt: 10_000,
  endedAt: 14_000,
  sid: SID,
  side: 'B',
  mapName: 'Forsaken City',
  result: 'death',
  durationMs: 41_000,
  endReason: 'restart',
  startRoom: '00',
  endRoom: '02',
  route: [
    { room: '00', enteredAt: 10_000, sessionTimeMs: null },
    { room: '01', enteredAt: 11_000, sessionTimeMs: 4_000 },
    { room: '02', enteredAt: 12_000, sessionTimeMs: 9_000 },
  ],
  deaths: [{ deathId: 3, room: '02', x: 7, y: 9, at: 13_000, timeInRoomMs: 41_000, first: true }],
  ...over,
});

describe('AttemptDetailPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttemptDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '7' })) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  /** Crée la page et répond à sa première requête (sans toucher aux autres, comme celles du bandeau « En jeu »). */
  function open(answer: (req: ReturnType<HttpTestingController['expectOne']>) => void) {
    const fixture = TestBed.createComponent(AttemptDetailPage);
    fixture.detectChanges();
    tick(0);
    answer(http.expectOne('/api/attempts/7'));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche un squelette pendant le chargement', fakeAsync(() => {
    const fixture = TestBed.createComponent(AttemptDetailPage);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.ds-skeleton')).not.toBeNull();
    discardPeriodicTasks();
  }));

  it('résume une tentative morte, avec son parcours et sa première mort', fakeAsync(() => {
    const el = open((req) => req.flush(attempt()));

    expect(el.querySelector('h1')?.textContent).toContain('Forsaken City');
    expect(el.querySelector('.lead')?.textContent).toContain('Deathless perdu en room 02 après 0:41');
    expect(el.querySelectorAll('.route .step').length).toBe(3);
    expect(el.querySelector('.route .step:first-child .when')?.textContent).toContain('Départ');
    expect(el.querySelector('.route .step:nth-child(2) .when')?.textContent).toContain('0:04');
    expect(el.querySelector('.route .step.stop')?.textContent).toContain('fin du deathless');
    expect(el.querySelector('tr.first-death')?.textContent).toContain('Première');

    discardPeriodicTasks();
  }));

  it('mène aux pages de la room et de la face', fakeAsync(() => {
    const el = open((req) => req.flush(attempt()));

    const room = el.querySelector('.route a.room-name') as HTMLAnchorElement;
    expect(room.getAttribute('href')).toContain('/maps/b/00');
    expect(decodeURIComponent(room.getAttribute('href') ?? '')).toContain(`sid=${SID}`);
    expect(el.querySelector('.links')?.textContent).toContain('Voir la face');

    discardPeriodicTasks();
  }));

  it('montre un deathless réussi avec la fraise et sans mort', fakeAsync(() => {
    const el = open((req) =>
      req.flush(attempt({ result: 'success', durationMs: 83_000, endReason: 'completed', endRoom: null, deaths: [] })),
    );

    expect(el.querySelector('.lead')?.textContent).toContain('Deathless réussi en 1:23');
    expect(el.querySelector('.step.win')).not.toBeNull();
    expect(el.querySelector('.route .step.stop')).toBeNull();
    expect(el.textContent).toContain("Aucune mort : c'est un deathless.");

    discardPeriodicTasks();
  }));

  it("dit que la tentative est introuvable sur un 404", fakeAsync(() => {
    const el = open((req) => req.flush('', { status: 404, statusText: 'Not Found' }));

    expect(el.textContent).toContain('Tentative introuvable');
    expect(el.querySelector('.ds-alert--danger')).toBeNull();

    discardPeriodicTasks();
  }));

  it('annonce une erreur réessayée automatiquement sur une autre erreur', fakeAsync(() => {
    const el = open((req) => req.flush('', { status: 500, statusText: 'Server Error' }));

    expect(el.querySelector('.ds-alert--danger')?.textContent).toContain('Réessai automatique');

    discardPeriodicTasks();
  }));
});
