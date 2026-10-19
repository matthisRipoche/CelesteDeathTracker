import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { countBy, mapName, modName } from '../../core/map-stats';
import { Death } from '../../core/death.model';
import { DeathService } from '../../core/death.service';
import { Shell } from '../../shared/shell/shell';
import { Sprite } from '../../shared/sprite/sprite';

type State = { status: 'loading' } | { status: 'error' } | { status: 'ok'; deaths: Death[] };

/** Liste des maps jouées, triées par nombre de morts. Un clic ouvre le détail (/map?sid=…). */
@Component({
  selector: 'app-maps',
  imports: [RouterLink, Shell, Sprite],
  templateUrl: './maps.html',
  styleUrl: './maps.css',
})
export class Maps {
  private readonly deathService = inject(DeathService);

  protected readonly state = toSignal(
    this.deathService.getDeaths().pipe(
      map((deaths): State => ({ status: 'ok', deaths })),
      catchError(() => of<State>({ status: 'error' })),
      startWith<State>({ status: 'loading' }),
    ),
    { requireSync: true },
  );

  protected readonly maps = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? countBy(s.deaths, (d) => d.chapter) : [];
  });

  protected readonly max = computed(() => this.maps()[0]?.count ?? 1);

  protected readonly mapName = mapName;
  protected readonly modName = modName;
}
