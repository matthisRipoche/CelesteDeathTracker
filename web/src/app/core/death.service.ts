import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Death } from './death.model';

/**
 * Service = classe injectable, partagée dans toute l'app.
 * Il isole les appels HTTP : les composants n'ont pas à connaître les URLs.
 */
@Injectable({ providedIn: 'root' })
export class DeathService {
  private readonly http = inject(HttpClient);

  // Relatif : en dev, proxy.conf.json redirige /api vers http://localhost:5000
  getDeaths(): Observable<Death[]> {
    return this.http.get<Death[]>('/api/deaths');
  }
}
