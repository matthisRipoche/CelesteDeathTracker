import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { countBy, mapName, modName } from '../../core/map-stats';
import { DeathsLive } from '../../core/deaths-live';
import { Shell } from '../../shared/shell/shell';
import { Sprite } from '../../shared/sprite/sprite';

/** Liste des maps jouées, triées par nombre de morts. Un clic ouvre le détail (/map?sid=…). */
@Component({
  selector: 'app-maps',
  imports: [RouterLink, Shell, Sprite],
  templateUrl: './maps.html',
  styleUrl: './maps.css',
})
export class Maps {
  // Flux partagé et tenu à jour toutes les 3 s : la liste suit les morts sans recharger la page.
  protected readonly state = toSignal(inject(DeathsLive).state$, { requireSync: true });

  protected readonly maps = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? countBy(s.deaths, (d) => d.chapter) : [];
  });

  protected readonly max = computed(() => this.maps()[0]?.count ?? 1);

  protected readonly mapName = mapName;
  protected readonly modName = modName;
}
