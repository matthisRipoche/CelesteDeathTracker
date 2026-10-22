import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Summary } from './summary.model';

/** Le tableau de bord. Le fuseau du navigateur part avec la requête : « aujourd'hui » commence à minuit chez le joueur. */
@Injectable({ providedIn: 'root' })
export class SummaryService {
  private readonly http = inject(HttpClient);

  summary(): Observable<Summary> {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return this.http.get<Summary>('/api/summary', { params: new HttpParams().set('tz', tz) });
  }
}
