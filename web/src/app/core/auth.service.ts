import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';

export interface User {
  id: number;
  // null pour un compte créé par Discord (on ne demande pas l'email à Discord)
  email: string | null;
  createdAt: string;
  discordUsername: string | null;
  discordAvatarUrl: string | null;
}

const RETURN_URL_KEY = 'returnUrl';

/**
 * Porte l'état "qui est connecté ?" pour toute l'application.
 * Le cookie de session (HttpOnly) est géré par le navigateur : le JavaScript ne voit
 * jamais de mot de passe ni de token, il sait seulement si /api/auth/me répond 200.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  // undefined = pas encore su ; null = non connecté ; User = connecté
  private readonly _user = signal<User | null | undefined>(undefined);

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => !!this._user());
  readonly isKnown = computed(() => this._user() !== undefined);

  loadCurrentUser(): Observable<User | null> {
    return this.http.get<User>('/api/auth/me').pipe(
      tap((u) => this._user.set(u)),
      catchError(() => {
        this._user.set(null);
        return of(null);
      }),
    );
  }

  login(email: string, password: string): Observable<User> {
    return this.http
      .post<User>('/api/auth/login', { email, password })
      .pipe(tap((u) => this._user.set(u)));
  }

  register(email: string, password: string): Observable<User> {
    return this.http.post<User>('/api/auth/register', { email, password });
  }

  /** Discord est-il configuré côté serveur ? Sinon on n'affiche pas le bouton. */
  providers(): Observable<{ discord: boolean }> {
    return this.http
      .get<{ discord: boolean }>('/api/auth/providers')
      .pipe(catchError(() => of({ discord: false })));
  }

  /** Rattachement : le serveur note quel compte le demande, puis la page redirige vers Discord. */
  startDiscordLink(): Observable<void> {
    return this.http.post<void>('/api/auth/discord/link', {});
  }

  /**
   * Discord quitte puis recharge le site : on garde en mémoire la page voulue (ex. /pair) pour
   * y revenir. Seuls les chemins internes sont acceptés (sinon redirection ouverte).
   */
  rememberReturnUrl(url: string): void {
    try {
      if (url.startsWith('/') && !url.startsWith('//')) sessionStorage.setItem(RETURN_URL_KEY, url);
    } catch {
      // stockage indisponible (navigation privée) : on retombera sur l'accueil
    }
  }

  takeReturnUrl(): string | null {
    try {
      const url = sessionStorage.getItem(RETURN_URL_KEY);
      sessionStorage.removeItem(RETURN_URL_KEY);
      return url && url.startsWith('/') && !url.startsWith('//') ? url : null;
    } catch {
      return null;
    }
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(tap(() => this._user.set(null)));
  }

  /** Appelé par l'intercepteur quand l'API répond 401 (session expirée). */
  clear(): void {
    this._user.set(null);
  }
}
