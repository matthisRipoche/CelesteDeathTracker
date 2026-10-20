import { Component, DestroyRef, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatClock, shortMapName } from '../../core/format';
import { LiveFeed } from '../../core/live-feed';

/**
 * Bandeau doré « En jeu » : visible sur toutes les pages tant qu'une tentative est ouverte, il mène au
 * Direct. Le chrono tourne chaque seconde ; il n'est volontairement pas annoncé par les lecteurs d'écran
 * (pas de role="status"), sinon il parlerait toutes les secondes.
 */
@Component({
  selector: 'app-live-banner',
  imports: [RouterLink],
  template: `
    @if (feed.current(); as c) {
      <!-- Repère nommé : sans lui, ce bandeau serait le seul contenu hors des zones de la page. -->
      <aside class="banner" aria-label="Partie en cours">
        <span class="dot" aria-hidden="true"></span>
        <span class="text">
          <strong>En jeu :</strong> {{ title() }}, face {{ c.side }}, room {{ c.room }}, {{ clock() }}
        </span>
        <a routerLink="/live" class="more">Voir le direct</a>
      </aside>
    }
  `,
  styles: `
    .banner {
      display: flex; align-items: center; gap: var(--space-3);
      min-height: 44px; padding: 0 var(--space-5);
      background: var(--color-highlight); color: var(--color-on-highlight);
      font-size: var(--text-sm);
    }
    .dot {
      width: 10px; height: 10px; border-radius: 50%; flex: none;
      background: var(--color-on-highlight);
      animation: pulse 1.6s ease-in-out infinite;
    }
    .text { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .more { margin-left: auto; font-weight: 600; white-space: nowrap; color: var(--color-on-highlight); }
    .more:hover { background: var(--color-on-highlight); color: var(--color-highlight); }
    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
    @media (prefers-reduced-motion: reduce) { .dot { animation: none; } }
  `,
})
export class LiveBanner {
  protected readonly feed = inject(LiveFeed);

  constructor() {
    this.feed.acquireFor(inject(DestroyRef));
  }

  protected readonly title = computed(() => {
    const c = this.feed.current();
    return c ? (c.mapName ?? shortMapName(c.sid)) : '';
  });

  protected readonly clock = computed(() => {
    const c = this.feed.current();
    return c ? formatClock(this.feed.now() - c.startedAt) : '';
  });
}
