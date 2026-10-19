import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FaceService } from '../../core/face.service';
import { modOf, shortMapName } from '../../core/format';
import { livePoll } from '../../core/live-poll';
import { PxBerry } from '../../shared/pixel/pixel-icons';
import { Shell } from '../../shared/shell/shell';

/**
 * Mes maps : une ligne par FACE jouée (une map + A, B ou C), la plus récente d'abord.
 * Un clic ouvre le détail de la face (/maps/:side?sid=…). Tenu à jour toutes les 3 s.
 */
@Component({
  selector: 'app-maps',
  imports: [DatePipe, RouterLink, Shell, PxBerry],
  templateUrl: './maps.html',
  styleUrl: './maps.css',
})
export class Maps {
  // inject() doit s'exécuter à la construction du composant, pas plus tard dans la requête.
  private readonly faceService = inject(FaceService);

  protected readonly state = toSignal(livePoll(() => this.faceService.faces()), { requireSync: true });

  protected readonly faces = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data : [];
  });

  protected readonly title = (f: { sid: string; mapName: string | null }) => f.mapName ?? shortMapName(f.sid);
  protected readonly mod = modOf;
}
