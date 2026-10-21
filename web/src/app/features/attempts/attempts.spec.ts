import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AttemptPage, AttemptRow } from '../../core/attempt.model';
import { Attempts } from './attempts';

const SID = 'Celeste/1-ForsakenCity';

const row = (over: Partial<AttemptRow> = {}): AttemptRow => ({
  id: 1,
  startedAt: 1_000,
  sid: SID,
  side: 'B',
  mapName: 'Forsaken City',
  result: 'success',
  durationMs: 83_000,
  endRoom: null,
  deaths: 0,
  ...over,
});

const page = (over: Partial<AttemptPage> = {}): AttemptPage => ({ items: [row()], page: 0, size: 20, total: 1, ...over });

describe('Attempts', () => {
  let http: HttpTestingController;

  function setup(query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [Attempts],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(query)) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  /** Crée la page (le premier cycle de détection lance la requête) et la renvoie sans toucher aux autres (bandeau « En jeu »). */
  function open() {
    const fixture = TestBed.createComponent(Attempts);
    fixture.detectChanges();
    return fixture;
  }

  const listRequest = () => http.expectOne((r) => r.url === '/api/attempts');

  it('affiche un squelette pendant le chargement', () => {
    setup();
    const fixture = open();
    expect((fixture.nativeElement as HTMLElement).querySelector('.ds-skeleton')).not.toBeNull();
    listRequest();
  });

  it('demande la première page, toutes périodes, puis affiche les tentatives', () => {
    setup();
    const fixture = open();
    const req = listRequest();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('period')).toBe('all');
    expect(req.request.params.has('result')).toBeFalse();
    req.flush(page({ items: [row(), row({ id: 2, result: 'death', durationMs: 41_000, endRoom: '02', deaths: 2 })], total: 2 }));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('tbody tr').length).toBe(2);
    expect(el.textContent).toContain('2 tentatives');
    expect(el.textContent).toContain('Réussie');
    expect(el.textContent).toContain('Fraise');
    expect(el.textContent).toContain('1:23');
    const link = el.querySelector('tbody a.open') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/attempts/1');
  });

  it("transmet la face de l'adresse au serveur et propose de la retirer", () => {
    setup({ sid: SID, side: 'b' });
    const fixture = open();
    const req = listRequest();
    expect(req.request.params.get('sid')).toBe(SID);
    expect(req.request.params.get('side')).toBe('B');
    req.flush(page());
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Retirer ce filtre');
  });

  it('relance la requête depuis la première page quand un filtre change', () => {
    setup();
    const fixture = open();
    listRequest().flush(page({ total: 45, items: [row()] }));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('.pager button:last-child') as HTMLButtonElement).click();
    fixture.detectChanges();
    const second = listRequest();
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page({ page: 1, total: 45 }));
    fixture.detectChanges();

    const select = el.querySelector('#f-result') as HTMLSelectElement;
    select.value = 'death';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const filtered = listRequest();
    expect(filtered.request.params.get('result')).toBe('death');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush(page());
  });

  it("n'affiche pas de pagination quand tout tient sur une page", () => {
    setup();
    const fixture = open();
    listRequest().flush(page());
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.pager')).toBeNull();
  });

  it("distingue l'historique vide du résultat vide d'un filtre", () => {
    setup();
    const fixture = open();
    listRequest().flush(page({ items: [], total: 0 }));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain("Aucune tentative pour l'instant");

    const select = (fixture.nativeElement as HTMLElement).querySelector('#f-period') as HTMLSelectElement;
    select.value = '7d';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    listRequest().flush(page({ items: [], total: 0 }));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucune tentative avec ces filtres');
  });

  it('annonce une erreur de chargement', () => {
    setup();
    const fixture = open();
    listRequest().flush('', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.ds-alert--danger')).not.toBeNull();
  });
});
