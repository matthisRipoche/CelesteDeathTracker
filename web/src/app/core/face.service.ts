import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FaceDetail, FaceSummary, RoomDetail, Side } from './face.model';

/** Statistiques par face de map. Le SID contient des « / » : il part en paramètre de requête. */
@Injectable({ providedIn: 'root' })
export class FaceService {
  private readonly http = inject(HttpClient);

  faces(): Observable<FaceSummary[]> {
    return this.http.get<FaceSummary[]>('/api/faces');
  }

  detail(sid: string, side: Side): Observable<FaceDetail> {
    return this.http.get<FaceDetail>('/api/faces/detail', { params: new HttpParams().set('sid', sid).set('side', side) });
  }

  /** Détail d'une room. Elle s'identifie par (sid, side, room) : un même nom existe dans d'autres maps. */
  room(sid: string, side: Side, room: string): Observable<RoomDetail> {
    return this.http.get<RoomDetail>('/api/faces/room', {
      params: new HttpParams().set('sid', sid).set('side', side).set('room', room),
    });
  }
}
