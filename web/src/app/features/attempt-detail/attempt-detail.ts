import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map, switchMap } from 'rxjs';
import { AttemptDetail } from '../../core/attempt.model';
import { AttemptService } from '../../core/attempt.service';
import { AttemptResult } from '../../core/face.model';
import { formatClock, formatDuration, modOf, shortMapName } from '../../core/format';
import { livePoll } from '../../core/live-poll';
import { PxBerry, PxBurst } from '../../shared/pixel/pixel-icons';
import { Shell } from '../../shared/shell/shell';

const RESULT_LABEL: Record<AttemptResult, string> = {
  success: 'Réussie',
  death: 'Morte',
  abandoned: 'Abandonnée',
  ongoing: 'En cours',
};

const END_REASON_LABEL: Record<string, string> = {
  completed: 'Map terminée',
  restart: 'Recommencée',
  golden_restart: 'Recommencée (fraise dorée)',
  give_up: 'Abandonnée',
  save_and_quit: 'Sauvegardée et quittée',
  switched: 'Changement de map',
};

/**
 * Détail d'une tentative (/attempts/:id) : le parcours room par room et les morts. Une tentative en cours
 * reste à jour (interrogation toutes les 3 s, voir core/live-poll.ts).
 */
@Component({
  selector: 'app-attempt-detail',
  imports: [DatePipe, RouterLink, Shell, PxBerry, PxBurst],
  templateUrl: './attempt-detail.html',
  styleUrl: './attempt-detail.css',
})
export class AttemptDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly attempts = inject(AttemptService);

  protected readonly state = toSignal(
    this.route.paramMap.pipe(
      map((p) => Number(p.get('id'))),
      switchMap((id) => livePoll(() => this.attempts.detail(id))),
    ),
    { requireSync: true },
  );

  protected readonly detail = computed<AttemptDetail | null>(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data : null;
  });

  protected readonly notFound = computed(() => {
    const s = this.state();
    return s.status === 'error' && s.code === 404;
  });

  protected readonly title = computed(() => {
    const d = this.detail();
    return d ? (d.mapName ?? shortMapName(d.sid)) : '';
  });

  protected readonly mod = computed(() => modOf(this.detail()?.sid ?? ''));

  /** Le parcours, avec pour chaque room les morts qui y ont eu lieu et si c'est là que la tentative s'arrête. */
  protected readonly steps = computed(() => {
    const d = this.detail();
    if (!d) return [];
    return d.route.map((s) => {
      const deaths = d.deaths.filter((x) => x.room === s.room);
      return {
        ...s,
        deaths,
        stop: d.result !== 'success' && s.room === d.endRoom,
      };
    });
  });

  protected readonly summary = computed(() => {
    const d = this.detail();
    if (!d) return '';
    switch (d.result) {
      case 'success':
        return `Deathless réussi en ${formatDuration(d.durationMs)}.`;
      case 'death':
        return `Deathless perdu en room ${d.endRoom ?? '?'} après ${formatDuration(d.durationMs)}.`;
      case 'ongoing':
        return 'Tentative en cours.';
      default:
        return d.endRoom ? `Tentative abandonnée en room ${d.endRoom}.` : 'Tentative abandonnée.';
    }
  });

  protected readonly endReason = (r: string | null) => (r ? (END_REASON_LABEL[r] ?? r) : '—');
  protected readonly fmt = formatDuration;
  protected readonly clock = formatClock;
  protected readonly label = (r: AttemptResult) => RESULT_LABEL[r];
}
