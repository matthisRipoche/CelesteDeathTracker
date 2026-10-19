import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';
import { devRoutes } from './dev-routes';
import { Dashboard } from './features/dashboard/dashboard';
import { Login } from './features/login/login';
import { MapDetail } from './features/map-detail/map-detail';
import { Maps } from './features/maps/maps';
import { Profile } from './features/profile/profile';
import { Pair } from './features/pair/pair';
import { Tokens } from './features/tokens/tokens';

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard] },
  { path: 'pair', component: Pair, canActivate: [authGuard] },
  { path: 'tokens', component: Tokens, canActivate: [authGuard] },
  { path: 'maps', component: Maps, canActivate: [authGuard] },
  { path: 'map', component: MapDetail, canActivate: [authGuard] },
  { path: 'profile', component: Profile, canActivate: [authGuard] },

  // Vitrine du design system : développement uniquement (voir dev-routes.ts). En production,
  // ce fichier est remplacé par une liste vide à la compilation.
  ...devRoutes,

  { path: '', component: Dashboard, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
