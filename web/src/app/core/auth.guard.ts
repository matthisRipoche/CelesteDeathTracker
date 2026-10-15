import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Garde de route : protège une page. Si on ne sait pas encore si l'utilisateur est connecté
 * (rechargement de la page), on interroge /api/auth/me avant de décider.
 * L'adresse demandée est passée à /login (returnUrl) pour y revenir après la connexion :
 * indispensable pour /pair, que le jeu ouvre directement dans le navigateur.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toLogin = router.createUrlTree(['/login'], {
    queryParams: state.url === '/' ? {} : { returnUrl: state.url },
  });

  if (auth.isKnown()) {
    return auth.isAuthenticated() ? true : toLogin;
  }
  return auth.loadCurrentUser().pipe(map((u) => (u ? true : toLogin)));
};

/** Inverse : la page de login n'a pas d'intérêt pour quelqu'un de déjà connecté. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toHome = router.parseUrl('/');

  if (auth.isKnown()) {
    return auth.isAuthenticated() ? toHome : true;
  }
  return auth.loadCurrentUser().pipe(map((u) => (u ? toHome : true)));
};
