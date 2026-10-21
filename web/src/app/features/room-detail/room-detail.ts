import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { combineLatest, map, switchMap } from 'rxjs';
import { RoomDetail, Side } from '../../core/face.model';
import { FaceService } from '../../core/face.service';
import { modOf, percent, shortMapName } from '../../core/format';
import { livePoll } from '../../core/live-poll';
import { outcomeTrend } from '../../core/room-stats';
import { RoomDeaths } from '../../shared/charts/room-deaths';
import { RoomOutcomes } from '../../shared/charts/room-outcomes';
import { Shell } from '../../shared/shell/shell';

/** Nombre de morts listées dans « Dernières morts ». */
const RECENT_DEATHS = 10;

/**
 * Détail d'une room (/maps/:side/:room?sid=…) : à quel endroit précis le deathless casse, et si on la
 * franchit de plus en plus souvent. Tenu à jour toutes les 3 s (voir core/live-poll.ts).
 */
@Component({
  selector: 'app-room-detail',
  imports: [DatePipe, RouterLink, Shell, RoomDeaths, RoomOutcomes],
  templateUrl: './room-detail.html',
  styleUrl: './room-detail.css',
})
export class RoomDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly faces = inject(FaceService);

  private readonly key = combineLatest([
    this.route.queryParamMap.pipe(map((p) => p.get('sid') ?? '')),
    this.route.paramMap.pipe(map((p): [Side, string] => [(p.get('side') ?? 'a').toUpperCase() as Side, p.get('room') ?? ''])),
  ]).pipe(map(([sid, [side, room]]) => ({ sid, side, room })));

  protected readonly target = toSignal(this.key, { requireSync: true });

  protected readonly state = toSignal(
    this.key.pipe(switchMap(({ sid, side, room }) => livePoll(() => this.faces.room(sid, side, room)))),
    { requireSync: true },
  );

  protected readonly detail = computed<RoomDetail | null>(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data : null;
  });

  protected readonly notFound = computed(() => {
    const s = this.state();
    return s.status === 'error' && s.code === 404;
  });

  protected readonly title = computed(() => this.detail()?.mapName ?? shortMapName(this.target().sid));
  protected readonly mod = computed(() => modOf(this.target().sid));
  protected readonly faceLabel = computed(() => `Face ${this.target().side}`);
  protected readonly trend = computed(() => outcomeTrend(this.detail()?.outcomes ?? []));

  /** Les morts les plus récentes d'abord. */
  protected readonly recentDeaths = computed(() => (this.detail()?.points ?? []).slice(-RECENT_DEATHS).reverse());

  protected readonly intro = computed(() => {
    const d = this.detail();
    if (!d) return '';
    const arrived = `${d.entries} tentative${d.entries > 1 ? 's' : ''} ${d.entries > 1 ? 'sont arrivées' : 'est arrivée'} ici`;
    if (d.stops === 0) return `${arrived} et aucune ne s'y est arrêtée.`;
    return `${arrived}, ${d.stops} s'y ${d.stops > 1 ? 'sont arrêtées' : 'est arrêtée'}, soit ${percent(d.stopRate)}.`;
  });

  protected readonly pct = percent;

  protected side(d: RoomDetail): string {
    return d.side.toLowerCase();
  }
}
