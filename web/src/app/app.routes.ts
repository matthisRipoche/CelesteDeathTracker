import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';
import { Dashboard } from './features/dashboard/dashboard';
import { Login } from './features/login/login';
import { Pair } from './features/pair/pair';
import { Tokens } from './features/tokens/tokens';

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard] },
  { path: 'pair', component: Pair, canActivate: [authGuard] },
  { path: 'tokens', component: Tokens, canActivate: [authGuard] },
  { path: '', component: Dashboard, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
