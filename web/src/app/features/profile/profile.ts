import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { countBy } from '../../core/map-stats';
import { DeathService } from '../../core/death.service';
import { TokenService } from '../../core/token.service';
import { Shell } from '../../shared/shell/shell';

/** Profil du compte connecté : identité et résumé. Rien n'est modifiable côté API pour l'instant. */
@Component({
  selector: 'app-profile',
  imports: [DatePipe, DecimalPipe, RouterLink, Shell],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile {
  protected readonly user = inject(AuthService).user;

  private readonly deaths = toSignal(
    inject(DeathService).getDeaths().pipe(catchError(() => of([]))),
    { initialValue: [] },
  );
  private readonly tokens = toSignal(
    inject(TokenService).list().pipe(catchError(() => of([]))),
    { initialValue: [] },
  );

  protected readonly total = computed(() => this.deaths().length);
  protected readonly mapCount = computed(() => countBy(this.deaths(), (d) => d.chapter).length);
  protected readonly activeDevices = computed(() => this.tokens().filter((t) => !t.revoked).length);

  /** Initiale affichée dans l'avatar (pas d'image téléversable pour l'instant). */
  protected readonly initial = computed(() => (this.user()?.email ?? '?').charAt(0).toUpperCase());
}
