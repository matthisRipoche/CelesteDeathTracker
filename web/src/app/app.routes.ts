import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';
import { devRoutes } from './dev-routes';
import { AttemptDetailPage } from './features/attempt-detail/attempt-detail';
import { Attempts } from './features/attempts/attempts';
import { Dashboard } from './features/dashboard/dashboard';
import { Login } from './features/login/login';
import { FaceDetailPage } from './features/face-detail/face-detail';
import { Live } from './features/live/live';
import { Maps } from './features/maps/maps';
import { Profile } from './features/profile/profile';
import { Pair } from './features/pair/pair';
import { RoomDetailPage } from './features/room-detail/room-detail';
import { Tokens } from './features/tokens/tokens';

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard] },
  { path: 'pair', component: Pair, canActivate: [authGuard] },
  { path: 'account', component: Profile, canActivate: [authGuard] },
  { path: 'account/devices', component: Tokens, canActivate: [authGuard] },
  // Anciennes adresses du compte et des appareils.
  { path: 'tokens', redirectTo: 'account/devices' },
  { path: 'live', component: Live, canActivate: [authGuard] },
  { path: 'attempts', component: Attempts, canActivate: [authGuard] },
  { path: 'attempts/:id', component: AttemptDetailPage, canActivate: [authGuard] },
  { path: 'maps', component: Maps, canActivate: [authGuard] },
  { path: 'maps/:side', component: FaceDetailPage, canActivate: [authGuard] },
  { path: 'maps/:side/:room', component: RoomDetailPage, canActivate: [authGuard] },
  // Ancienne adresse du détail d'une map (par SID seul) : la face est désormais dans le chemin.
  { path: 'map', redirectTo: 'maps' },
  { path: 'profile', redirectTo: 'account' },

  // Vitrine du design system : développement uniquement (voir dev-routes.ts). En production,
  // ce fichier est remplacé par une liste vide à la compilation.
  ...devRoutes,

  { path: '', component: Dashboard, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
