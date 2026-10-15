import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';

export interface User {
  id: number;
  email: string;
  createdAt: string;
}

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

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(tap(() => this._user.set(null)));
  }

  /** Appelé par l'intercepteur quand l'API répond 401 (session expirée). */
  clear(): void {
    this._user.set(null);
  }
}
