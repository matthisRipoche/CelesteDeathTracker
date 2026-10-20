import { DestroyRef, Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { LiveSnapshot } from './live.model';

/** Fabrique de flux : remplaçable dans les tests (EventSource n'existe pas hors d'un vrai navigateur). */
export type EventSourceFactory = (url: string) => EventSource;
export const EVENT_SOURCE_FACTORY = new InjectionToken<EventSourceFactory>('EVENT_SOURCE_FACTORY', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

export type LiveConnection = 'idle' | 'connecting' | 'open' | 'reconnecting';

const STREAM_URL = '/api/live/stream';
// Garde le flux ouvert un court instant après la dernière page : en changeant de page, on ne se
// reconnecte pas (et on garde l'état déjà reçu).
const KEEP_ALIVE_MS = 10_000;
const CLOCK_MS = 1000;

/**
 * Le live de session, partagé par toute l'application (bandeau « En jeu » et page Direct).
 *
 * Une seule connexion Server-Sent Events, ouverte tant qu'une page écoute (acquire) : le serveur envoie
 * l'état complet à l'ouverture, puis à chaque événement du jeu. Le navigateur se reconnecte tout seul
 * (EventSource) si la connexion tombe, et le serveur renvoie alors l'état.
 *
 * Les chronos tournent côté navigateur, recalés sur l'horloge du serveur : `now` est l'heure « serveur »
 * estimée, mise à jour chaque seconde tant qu'une tentative est ouverte.
 */
@Injectable({ providedIn: 'root' })
export class LiveFeed {
  private readonly open = inject(EVENT_SOURCE_FACTORY);

  private readonly snapshotState = signal<LiveSnapshot | null>(null);
  private readonly connectionState = signal<LiveConnection>('idle');
  private readonly nowState = signal(Date.now());

  readonly snapshot = this.snapshotState.asReadonly();
  readonly connection = this.connectionState.asReadonly();
  /** Heure du serveur estimée à cet instant (rafraîchie chaque seconde quand c'est utile). */
  readonly now = this.nowState.asReadonly();
  readonly current = computed(() => this.snapshotState()?.current ?? null);

  private source: EventSource | null = null;
  private listeners = 0;
  private closeTimer: ReturnType<typeof setTimeout> | null = null;
  private clock: ReturnType<typeof setInterval> | null = null;
  private skew = 0; // heure serveur - heure du navigateur

  /** Une page commence à écouter. Rend la fonction à appeler quand elle s'arrête (ou à lier à DestroyRef). */
  acquire(): () => void {
    this.listeners++;
    if (this.closeTimer) {
      clearTimeout(this.closeTimer);
      this.closeTimer = null;
    }
    if (!this.source) {
      this.connect();
    }
    let released = false;
    return () => {
      if (released) return;
      released = true;
      this.listeners--;
      if (this.listeners === 0) {
        this.closeTimer = setTimeout(() => this.disconnect(), KEEP_ALIVE_MS);
      }
    };
  }

  /** Pratique pour un composant : écoute jusqu'à sa destruction. */
  acquireFor(destroyRef: DestroyRef): void {
    destroyRef.onDestroy(this.acquire());
  }

  private connect(): void {
    this.connectionState.set('connecting');
    const source = this.open(STREAM_URL);
    this.source = source;

    source.addEventListener('open', () => this.connectionState.set('open'));
    source.addEventListener('snapshot', (event) => {
      const snapshot = JSON.parse((event as MessageEvent<string>).data) as LiveSnapshot;
      this.skew = snapshot.serverTime - Date.now();
      this.snapshotState.set(snapshot);
      this.connectionState.set('open');
      this.tick();
      this.syncClock();
    });
    // Après une erreur, EventSource retente seul : on l'indique, sans rien fermer.
    source.addEventListener('error', () => this.connectionState.set('reconnecting'));
  }

  private disconnect(): void {
    this.source?.close();
    this.source = null;
    this.closeTimer = null;
    this.connectionState.set('idle');
    this.stopClock();
  }

  private tick(): void {
    this.nowState.set(Date.now() + this.skew);
  }

  /** L'horloge ne tourne que s'il y a une tentative ouverte à chronométrer. */
  private syncClock(): void {
    if (this.snapshotState()?.current && !this.clock) {
      this.clock = setInterval(() => this.tick(), CLOCK_MS);
    } else if (!this.snapshotState()?.current) {
      this.stopClock();
    }
  }

  private stopClock(): void {
    if (this.clock) {
      clearInterval(this.clock);
      this.clock = null;
    }
  }
}
