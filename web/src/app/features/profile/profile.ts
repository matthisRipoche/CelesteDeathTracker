import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { countBy } from '../../core/map-stats';
import { DeathsLive } from '../../core/deaths-live';
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
  private readonly auth = inject(AuthService);
  protected readonly user = this.auth.user;
  protected readonly providers = toSignal(this.auth.providers(), { initialValue: { discord: false } });

  // Retour de Discord : ?discord=lie (rattaché) ou ?discord=deja-lie (ce Discord est pris)
  protected readonly discordResult = inject(ActivatedRoute).snapshot.queryParamMap.get('discord');
  protected readonly linkBusy = signal(false);
  protected readonly linkError = signal(false);

  // Flux partagé et tenu à jour : le résumé suit les morts sans recharger la page.
  private readonly state = toSignal(inject(DeathsLive).state$, { requireSync: true });
  private readonly deaths = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.deaths : [];
  });
  private readonly tokens = toSignal(
    inject(TokenService).list().pipe(catchError(() => of([]))),
    { initialValue: [] },
  );

  protected readonly total = computed(() => this.deaths().length);
  protected readonly mapCount = computed(() => countBy(this.deaths(), (d) => d.chapter).length);
  protected readonly activeDevices = computed(() => this.tokens().filter((t) => !t.revoked).length);

  /** Le serveur note quel compte demande le lien, puis on part chez Discord (navigation complète). */
  protected linkDiscord(): void {
    this.linkBusy.set(true);
    this.linkError.set(false);
    this.auth.startDiscordLink().subscribe({
      next: () => (window.location.href = '/api/auth/oauth2/discord'),
      error: () => {
        this.linkBusy.set(false);
        this.linkError.set(true);
      },
    });
  }

  /** Initiale affichée dans l'avatar (pas d'image téléversable pour l'instant). */
  protected readonly initial = computed(() => (this.user()?.email ?? this.user()?.discordUsername ?? '?').charAt(0).toUpperCase());
}
