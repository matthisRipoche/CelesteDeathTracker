import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { combineLatest, map, switchMap } from 'rxjs';
import { AttemptResult, FaceDetail, Side } from '../../core/face.model';
import { FaceService } from '../../core/face.service';
import { formatDuration, modOf, percent, shortMapName } from '../../core/format';
import { livePoll } from '../../core/live-poll';
import { FaceEvolution } from '../../shared/charts/face-evolution';
import { PxBerry, PxBurst } from '../../shared/pixel/pixel-icons';
import { RoomPath } from '../../shared/room-path/room-path';
import { Shell } from '../../shared/shell/shell';

const RESULT_LABEL: Record<AttemptResult, string> = {
  success: 'Réussie',
  death: 'Morte',
  abandoned: 'Abandonnée',
  ongoing: 'En cours',
};

/**
 * Détail d'une face de map (/maps/:side?sid=…) : où les tentatives s'arrêtent, si on progresse, et les
 * dernières tentatives. Tenu à jour toutes les 3 s (voir core/live-poll.ts).
 */
@Component({
  selector: 'app-face-detail',
  imports: [DatePipe, RouterLink, Shell, FaceEvolution, RoomPath, PxBerry, PxBurst],
  templateUrl: './face-detail.html',
  styleUrl: './face-detail.css',
})
export class FaceDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly faces = inject(FaceService);

  protected readonly sid = toSignal(this.route.queryParamMap.pipe(map((p) => p.get('sid') ?? '')), {
    requireSync: true,
  });
  protected readonly side = toSignal(
    this.route.paramMap.pipe(map((p): Side => ((p.get('side') ?? 'a').toUpperCase() as Side))),
    { requireSync: true },
  );

  protected readonly state = toSignal(
    combineLatest([
      this.route.queryParamMap.pipe(map((p) => p.get('sid') ?? '')),
      this.route.paramMap.pipe(map((p) => (p.get('side') ?? 'a').toUpperCase() as Side)),
    ]).pipe(switchMap(([sid, side]) => livePoll(() => this.faces.detail(sid, side)))),
    { requireSync: true },
  );

  protected readonly detail = computed<FaceDetail | null>(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data : null;
  });

  protected readonly notFound = computed(() => {
    const s = this.state();
    return s.status === 'error' && s.code === 404;
  });

  protected readonly title = computed(() => this.detail()?.mapName ?? shortMapName(this.sid()));
  protected readonly mod = computed(() => modOf(this.sid()));

  /** La room où le plus de tentatives s'arrêtent : le « passage clé », surligné. */
  private readonly cruxRoom = computed(() => {
    const rooms = this.detail()?.rooms ?? [];
    const max = Math.max(0, ...rooms.map((r) => r.endings));
    return max > 0 ? rooms.find((r) => r.endings === max)?.room : undefined;
  });

  protected readonly rooms = computed(() => {
    const rooms = this.detail()?.rooms ?? [];
    const max = Math.max(1, ...rooms.map((r) => r.endings));
    return rooms.map((r) => ({
      ...r,
      width: r.endings === 0 ? 0 : Math.max(3, Math.round((r.endings / max) * 100)),
      crux: r.room === this.cruxRoom(),
    }));
  });

  protected readonly cruxShare = computed(() => {
    const d = this.detail();
    const crux = d?.rooms.find((r) => r.room === this.cruxRoom());
    return d && crux ? crux.endings / d.attempts : 0;
  });

  protected readonly clearsWidth = computed(() => {
    const d = this.detail();
    const max = Math.max(1, ...(d?.rooms.map((r) => r.endings) ?? [1]));
    return d && d.clears > 0 ? Math.max(3, Math.round((d.clears / max) * 100)) : 0;
  });

  /** Moyenne des durées des premières et des dernières tentatives : montre si on tient plus longtemps. */
  protected readonly trend = computed(() => {
    const known = (this.detail()?.evolution ?? []).filter((p) => p.durationMs != null);
    if (known.length < 6) return null;
    const n = Math.min(10, Math.floor(known.length / 2));
    const avg = (xs: typeof known) => xs.reduce((s, p) => s + (p.durationMs ?? 0), 0) / xs.length;
    const first = avg(known.slice(0, n));
    const last = avg(known.slice(-n));
    return { n, first, last, better: last > first };
  });

  protected readonly intro = computed(() => {
    const d = this.detail();
    if (!d) return '';
    const parts = [`${d.attempts} tentative${d.attempts > 1 ? 's' : ''}`];
    parts.push(
      d.clears === 0
        ? 'aucune enchaînée sans mourir pour l’instant'
        : `${d.clears} enchaînée${d.clears > 1 ? 's' : ''} sans mourir`,
    );
    const crux = this.cruxRoom();
    const sentence = parts.join(', ') + '.';
    return crux ? `${sentence} La room ${crux} concentre ${percent(this.cruxShare())} des tentatives.` : sentence;
  });

  protected readonly faceLabel = computed(() => `Face ${this.side()}`);
  protected readonly roomCount = computed(() => this.detail()?.rooms.length ?? 0);

  protected readonly fmt = formatDuration;
  protected readonly pct = percent;
  protected readonly label = (r: AttemptResult) => RESULT_LABEL[r];
}
