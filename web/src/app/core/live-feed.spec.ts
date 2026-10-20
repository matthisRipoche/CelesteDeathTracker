import { discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { EVENT_SOURCE_FACTORY, LiveFeed } from './live-feed';
import { LiveSnapshot } from './live.model';

/** Un EventSource qui ne parle à personne : le test lui fait « recevoir » des messages. */
class FakeEventSource {
  closed = false;
  private readonly listeners = new Map<string, ((e: unknown) => void)[]>();

  constructor(readonly url: string) {}

  addEventListener(type: string, fn: (e: unknown) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn]);
  }

  close(): void {
    this.closed = true;
  }

  emit(type: string, data?: unknown): void {
    for (const fn of this.listeners.get(type) ?? []) fn(data === undefined ? {} : { data: JSON.stringify(data) });
  }
}

const snapshot = (over: Partial<LiveSnapshot> = {}): LiveSnapshot => ({
  active: true,
  session: null,
  current: {
    id: 1, sid: 'Celeste/1-ForsakenCity', side: 'B', mapName: 'Forsaken City', room: '08',
    startedAt: 1_000_000, lastEventAt: 1_030_000, deaths: 0, roomsVisited: 3,
  },
  serverTime: Date.now(),
  ...over,
});

describe('LiveFeed', () => {
  let sources: FakeEventSource[];

  beforeEach(() => {
    sources = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: EVENT_SOURCE_FACTORY,
          useValue: (url: string) => {
            const s = new FakeEventSource(url);
            sources.push(s);
            return s as unknown as EventSource;
          },
        },
      ],
    });
  });

  it('ouvre une seule connexion pour plusieurs pages, et la ferme 10 s après la dernière', fakeAsync(() => {
    const feed = TestBed.inject(LiveFeed);
    const releaseA = feed.acquire();
    const releaseB = feed.acquire();

    expect(sources.length).toBe(1);
    expect(sources[0].url).toBe('/api/live/stream');

    releaseA();
    tick(20_000);
    expect(sources[0].closed).toBeFalse(); // B écoute encore

    releaseB();
    tick(9_000);
    expect(sources[0].closed).toBeFalse(); // sursis
    tick(2_000);
    expect(sources[0].closed).toBeTrue();
    expect(feed.connection()).toBe('idle');
  }));

  it("garde la connexion quand on change de page (nouvelle écoute pendant le sursis)", fakeAsync(() => {
    const feed = TestBed.inject(LiveFeed);
    feed.acquire()();       // page A s'ouvre puis se ferme
    tick(3_000);
    const releaseB = feed.acquire(); // page B s'ouvre

    tick(30_000);
    expect(sources.length).toBe(1);
    expect(sources[0].closed).toBeFalse();

    releaseB();
    flush();
  }));

  it("reçoit l'état envoyé par le serveur", fakeAsync(() => {
    const feed = TestBed.inject(LiveFeed);
    const release = feed.acquire();
    expect(feed.connection()).toBe('connecting');
    expect(feed.snapshot()).toBeNull();

    sources[0].emit('snapshot', snapshot());

    expect(feed.connection()).toBe('open');
    expect(feed.current()?.room).toBe('08');

    // une mise à jour remplace l'état précédent
    sources[0].emit('snapshot', snapshot({ current: null, active: false }));
    expect(feed.current()).toBeNull();

    release();
    flush();
    discardPeriodicTasks();
  }));

  it('indique la reconnexion quand le flux tombe, sans se fermer', fakeAsync(() => {
    const feed = TestBed.inject(LiveFeed);
    const release = feed.acquire();
    sources[0].emit('snapshot', snapshot());

    sources[0].emit('error');

    expect(feed.connection()).toBe('reconnecting');
    expect(sources[0].closed).toBeFalse(); // EventSource retente tout seul

    release();
    flush();
    discardPeriodicTasks();
  }));

  it("fait avancer l'heure chaque seconde seulement tant qu'une tentative est ouverte", fakeAsync(() => {
    const feed = TestBed.inject(LiveFeed);
    const release = feed.acquire();
    sources[0].emit('snapshot', snapshot());

    const before = feed.now();
    tick(3_000);
    expect(feed.now() - before).toBeGreaterThanOrEqual(3_000 - 1_000);

    // plus de tentative : l'horloge s'arrête
    sources[0].emit('snapshot', snapshot({ current: null, active: false }));
    const stopped = feed.now();
    tick(5_000);
    expect(feed.now()).toBe(stopped);

    release();
    flush();
    discardPeriodicTasks();
  }));
});
