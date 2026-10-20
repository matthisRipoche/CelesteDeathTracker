import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RoomDetail } from '../../core/face.model';
import { RoomDetailPage } from './room-detail';

const SID = 'Celeste/1-ForsakenCity';

const room = (over: Partial<RoomDetail> = {}): RoomDetail => ({
  sid: SID,
  side: 'B',
  mapName: 'Forsaken City',
  room: '01',
  position: 2,
  roomCount: 3,
  previousRoom: '00',
  nextRoom: '02',
  entries: 4,
  stops: 2,
  stopRate: 0.5,
  deathsTotal: 1,
  lastDeathAt: 21_000,
  points: [{ deathId: 7, attemptId: 3, at: 21_000, x: 6853, y: -2200, first: true }],
  outcomes: [
    { attemptId: 1, startedAt: 1_000, outcome: 'cleared' },
    { attemptId: 3, startedAt: 20_000, outcome: 'died' },
  ],
  ...over,
});

describe('RoomDetailPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoomDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap({ sid: SID })),
            paramMap: of(convertToParamMap({ side: 'b', room: '01' })),
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  /** Crée la page et répond à sa première requête (sans toucher aux autres, comme celles du bandeau « En jeu »). */
  function open(answer: (req: ReturnType<HttpTestingController['expectOne']>) => void) {
    const fixture = TestBed.createComponent(RoomDetailPage);
    fixture.detectChanges();
    tick(0);
    const req = http.expectOne((r) => r.url === '/api/faces/room');
    expect(req.request.params.get('sid')).toBe(SID);
    expect(req.request.params.get('side')).toBe('B');
    expect(req.request.params.get('room')).toBe('01');
    answer(req);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche un squelette pendant le chargement', fakeAsync(() => {
    const fixture = TestBed.createComponent(RoomDetailPage);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.ds-skeleton')).not.toBeNull();
    discardPeriodicTasks();
  }));

  it('affiche les chiffres de la room et des liens vers les rooms voisines', fakeAsync(() => {
    const el = open((req) => req.flush(room()));

    expect(el.querySelector('h1')?.textContent).toContain('01');
    expect(el.querySelector('.lead')?.textContent).toContain('4 tentatives sont arrivées ici, 2 s\'y sont arrêtées, soit 50 %');
    expect(el.querySelector('.figures')?.textContent).toContain('50 %');

    const next = el.querySelector('a[rel=next]') as HTMLAnchorElement;
    expect(next.textContent).toContain('Room 02');
    expect(next.getAttribute('href')).toContain('/maps/b/02');
    expect(decodeURIComponent(next.getAttribute('href') ?? '')).toContain(`sid=${SID}`);
    expect((el.querySelector('a[rel=prev]') as HTMLAnchorElement).textContent).toContain('Room 00');

    discardPeriodicTasks();
  }));

  it("n'offre pas de lien vers une room voisine qui n'existe pas", fakeAsync(() => {
    const el = open((req) => req.flush(room({ position: 1, previousRoom: null })));

    expect(el.querySelector('a[rel=prev]')).toBeNull();
    expect(el.querySelector('a[rel=next]')).not.toBeNull();

    discardPeriodicTasks();
  }));

  it("dit qu'il n'y a aucune mort quand aucune n'est enregistrée ici", fakeAsync(() => {
    const el = open((req) => req.flush(room({ deathsTotal: 0, lastDeathAt: null, points: [] })));

    expect(el.textContent).toContain('Aucune mort dans cette room');
    expect(el.querySelector('app-room-deaths')).toBeNull();

    discardPeriodicTasks();
  }));

  it("dit qu'aucune tentative n'est arrivée dans la room sur un 404", fakeAsync(() => {
    const el = open((req) => req.flush('', { status: 404, statusText: 'Not Found' }));

    expect(el.textContent).toContain('Aucune tentative dans cette room');
    expect(el.querySelector('.ds-alert--danger')).toBeNull();

    discardPeriodicTasks();
  }));

  it('annonce une erreur réessayée automatiquement sur une autre erreur', fakeAsync(() => {
    const el = open((req) => req.flush('', { status: 500, statusText: 'Server Error' }));

    expect(el.querySelector('.ds-alert--danger')?.textContent).toContain('Réessai automatique');

    discardPeriodicTasks();
  }));
});
