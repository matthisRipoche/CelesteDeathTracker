import { Routes } from '@angular/router';

/**
 * Routes réservées au développement (`npm start`).
 * Le build de production remplace ce fichier par dev-routes.prod.ts (voir angular.json,
 * configuration "production" > fileReplacements) : la vitrine du design system n'entre donc ni
 * dans le bundle ni dans l'image Docker publiée.
 */
export const devRoutes: Routes = [
  {
    path: 'design-system',
    loadComponent: () => import('./features/design-system/design-system').then((m) => m.DesignSystem),
  },
];
