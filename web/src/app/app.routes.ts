import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';
import { devRoutes } from './dev-routes';
import { Dashboard } from './features/dashboard/dashboard';
import { Login } from './features/login/login';
import { FaceDetailPage } from './features/face-detail/face-detail';
import { Live } from './features/live/live';
import { Maps } from './features/maps/maps';
import { Profile } from './features/profile/profile';
import { Pair } from './features/pair/pair';
import { Tokens } from './features/tokens/tokens';

export const routes: Routes = [
  { path: 'login', title: 'Connexion', component: Login, canActivate: [guestGuard] },
  { path: 'pair', title: 'Associer un jeu', component: Pair, canActivate: [authGuard] },
  { path: 'tokens', title: 'Appareils', component: Tokens, canActivate: [authGuard] },
  { path: 'live', title: 'Direct', component: Live, canActivate: [authGuard] },
  { path: 'maps', title: 'Mes maps', component: Maps, canActivate: [authGuard] },
  { path: 'maps/:side', title: 'Détail d’une face', component: FaceDetailPage, canActivate: [authGuard] },
  // Ancienne adresse du détail d'une map (par SID seul) : la face est désormais dans le chemin.
  { path: 'map', redirectTo: 'maps' },
  { path: 'profile', title: 'Mon profil', component: Profile, canActivate: [authGuard] },

  // Vitrine du design system : développement uniquement (voir dev-routes.ts). En production,
  // ce fichier est remplacé par une liste vide à la compilation.
  ...devRoutes,

  { path: '', title: 'Tableau de bord', component: Dashboard, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
