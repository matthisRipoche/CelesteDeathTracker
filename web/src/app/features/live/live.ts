import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AttemptResult } from '../../core/face.model';
import { formatClock, formatDuration, shortMapName } from '../../core/format';
import { LiveFeed } from '../../core/live-feed';
import { PxBerry, PxBurst } from '../../shared/pixel/pixel-icons';
import { Shell } from '../../shared/shell/shell';

const RESULT_LABEL: Record<AttemptResult, string> = {
  success: 'Réussie',
  death: 'Morte',
  abandoned: 'Abandonnée',
  ongoing: 'En cours',
};

// Au-delà, on suggère que le jeu est en pause : il n'envoie rien tant qu'on ne change pas de room.
const QUIET_AFTER_MS = 90_000;

/**
 * Direct (/live) : la tentative en cours et les statistiques de la session, mises à jour dès que le jeu
 * envoie un événement (Server-Sent Events). Les chronos tournent côté navigateur.
 */
@Component({
  selector: 'app-live',
  imports: [DatePipe, RouterLink, Shell, PxBerry, PxBurst],
  templateUrl: './live.html',
  styleUrl: './live.css',
})
export class Live {
  protected readonly feed = inject(LiveFeed);

  constructor() {
    this.feed.acquireFor(inject(DestroyRef));
  }

  protected readonly snapshot = this.feed.snapshot;
  protected readonly current = this.feed.current;
  protected readonly session = computed(() => this.snapshot()?.session ?? null);
  protected readonly active = computed(() => this.snapshot()?.active ?? false);

  protected readonly title = computed(() => {
    const c = this.current();
    return c ? (c.mapName ?? shortMapName(c.sid)) : '';
  });

  /** Temps écoulé depuis le départ de la tentative en cours. */
  protected readonly elapsed = computed(() => {
    const c = this.current();
    return c ? formatClock(this.feed.now() - c.startedAt) : '0:00';
  });

  /** Temps de jeu de la session : le cumul des tentatives, plus ce qui court depuis le dernier événement. */
  protected readonly playTime = computed(() => {
    const s = this.session();
    const c = this.current();
    if (!s) return '0:00';
    return formatClock(s.activeMs + (c ? Math.max(0, this.feed.now() - c.lastEventAt) : 0));
  });

  protected readonly quiet = computed(() => {
    const c = this.current();
    return c ? this.feed.now() - c.lastEventAt > QUIET_AFTER_MS : false;
  });

  protected readonly quietFor = computed(() => {
    const c = this.current();
    return c ? formatClock(this.feed.now() - c.lastEventAt) : '';
  });

  protected readonly connectionLabel = computed(() => {
    switch (this.feed.connection()) {
      case 'open':
        return 'En direct';
      case 'reconnecting':
        return 'Reconnexion…';
      default:
        return 'Connexion…';
    }
  });

  protected readonly cruxIntro = computed(() => {
    const crux = this.session()?.crux;
    if (!crux) return '';
    return `La room ${crux.room} concentre ${crux.endings} arrêts de la session.`;
  });

  protected readonly fmt = formatDuration;
  protected readonly name = (f: { sid: string; mapName: string | null }) => f.mapName ?? shortMapName(f.sid);
  protected readonly label = (r: AttemptResult) => RESULT_LABEL[r];
}
