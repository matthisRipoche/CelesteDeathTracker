import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AttemptDetail, AttemptFilter, AttemptPage } from './attempt.model';

export const PAGE_SIZE = 20;

/** Historique des tentatives. Le SID contient des « / » : il part en paramètre de requête. */
@Injectable({ providedIn: 'root' })
export class AttemptService {
  private readonly http = inject(HttpClient);

  list(filter: AttemptFilter): Observable<AttemptPage> {
    let params = new HttpParams().set('period', filter.period).set('page', filter.page).set('size', PAGE_SIZE);
    if (filter.sid) params = params.set('sid', filter.sid);
    if (filter.side) params = params.set('side', filter.side);
    if (filter.result) params = params.set('result', filter.result);
    return this.http.get<AttemptPage>('/api/attempts', { params });
  }

  detail(id: number): Observable<AttemptDetail> {
    return this.http.get<AttemptDetail>(`/api/attempts/${id}`);
  }
}
