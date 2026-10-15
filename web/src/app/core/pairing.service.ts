import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface PairingRequestInfo {
  userCode: string;
  deviceName: string;
  requestedAt: string;
}

/** Côté navigateur de l'appairage : le jeu affiche un code, l'utilisateur l'approuve ici. */
@Injectable({ providedIn: 'root' })
export class PairingService {
  private readonly http = inject(HttpClient);

  lookup(code: string): Observable<PairingRequestInfo> {
    return this.http.get<PairingRequestInfo>('/api/pairing/lookup', {
      params: new HttpParams().set('code', code),
    });
  }

  approve(userCode: string): Observable<void> {
    return this.http.post<void>('/api/pairing/approve', { userCode });
  }

  deny(userCode: string): Observable<void> {
    return this.http.post<void>('/api/pairing/deny', { userCode });
  }
}
