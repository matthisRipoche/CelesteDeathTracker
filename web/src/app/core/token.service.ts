import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ApiToken {
  id: number;
  label: string;
  createdAt: string;
  lastUsedAt: string | null;
  revoked: boolean;
}

/** Renvoyé une seule fois, à la création : seul moment où le token en clair existe. */
export interface CreatedToken {
  id: number;
  label: string;
  createdAt: string;
  token: string;
}

@Injectable({ providedIn: 'root' })
export class TokenService {
  private readonly http = inject(HttpClient);

  list(): Observable<ApiToken[]> {
    return this.http.get<ApiToken[]>('/api/tokens');
  }

  create(label: string): Observable<CreatedToken> {
    return this.http.post<CreatedToken>('/api/tokens', { label });
  }

  revoke(id: number): Observable<void> {
    return this.http.delete<void>(`/api/tokens/${id}`);
  }
}
