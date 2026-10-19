import { Component, signal } from '@angular/core';

interface Swatch {
  token: string;
  hex: string;
  note?: string;
}

interface Sprite {
  file: string;
  label: string;
  scale: number;
}

type Period = 'jour' | 'semaine' | 'total';

/**
 * Page de démonstration du design system « Basecamp ».
 *
 * RÉSERVÉE AU DÉVELOPPEMENT : la route n'est déclarée que si isDevMode() (voir app.routes.ts),
 * donc elle n'existe ni dans le build de production ni dans l'image Docker publique.
 * Disponible avec `npm start` sur http://localhost:4200/design-system
 */
@Component({
  selector: 'app-design-system',
  templateUrl: './design-system.html',
  styleUrl: './design-system.css',
})
export class DesignSystem {
  protected readonly loading = signal(false);
  protected readonly period = signal<Period>('jour');
  protected readonly codeValue = signal('');
  protected readonly spritesMissing = signal(false);

  protected readonly sections = [
    ['principes', 'Principes'],
    ['couleurs', 'Couleurs'],
    ['typographie', 'Typographie'],
    ['forme', 'Espacement et forme'],
    ['boutons', 'Boutons'],
    ['formulaires', 'Formulaires'],
    ['cartes', 'Cartes et indicateurs'],
    ['donnees', 'Données'],
    ['retours', 'Retours et états'],
    ['navigation', 'Navigation'],
    ['exemple', 'Exemple de page'],
    ['assets', 'Assets du jeu (local)'],
  ] as const;

  protected readonly paper: Swatch[] = [
    { token: '--ds-paper', hex: '#eef1ee', note: 'papier glacier · fond clair' },
    { token: '--ds-paper-sunk', hex: '#e1e7e3', note: 'survol, barres, champs' },
    { token: '--ds-ink', hex: '#1b2a2e', note: 'encre de roche · texte, boutons' },
    { token: '--ds-granite', hex: '#53646a', note: 'texte secondaire · 5,4:1' },
    { token: '--ds-rule', hex: '#c5cfcb', note: 'filets fins' },
  ];

  protected readonly night: Swatch[] = [
    { token: '--ds-night', hex: '#0f181b', note: 'ardoise de nuit · fond sombre' },
    { token: '--ds-night-raised', hex: '#1a282c', note: 'survol, barres, champs' },
    { token: '--ds-night-chrome', hex: '#162226', note: 'barre du haut' },
    { token: '--ds-snow', hex: '#e7edea', note: 'texte · 15:1' },
    { token: '--ds-mist', hex: '#9db0ac', note: 'texte secondaire · 8,1:1' },
    { token: '--ds-night-rule', hex: '#2e4046', note: 'filets fins' },
  ];

  protected readonly signals: Swatch[] = [
    { token: '--ds-pine', hex: '#1f7a4d', note: 'sommet : réussite (clair)' },
    { token: '--ds-pine-light', hex: '#4fbf86', note: 'sommet (sombre)' },
    { token: '--ds-brick', hex: '#b5382a', note: 'chute : échec (clair)' },
    { token: '--ds-brick-light', hex: '#f0705f', note: 'chute (sombre)' },
    { token: '--ds-gold', hex: '#f2d31b', note: 'fraise dorée : le surligneur' },
  ];

  protected readonly semantic: Swatch[] = [
    { token: '--color-bg', hex: 'papier / ardoise' },
    { token: '--color-surface-raised', hex: 'papier creusé' },
    { token: '--color-border', hex: 'trait' },
    { token: '--color-border-strong', hex: 'encre' },
    { token: '--color-text', hex: 'encre / neige' },
    { token: '--color-text-muted', hex: 'granit / brume' },
    { token: '--color-highlight', hex: 'fraise dorée' },
    { token: '--color-success', hex: 'sommet' },
    { token: '--color-danger', hex: 'chute' },
    { token: '--color-chrome', hex: 'barre du haut' },
  ];

  protected readonly spacing = [1, 2, 3, 4, 5, 6, 7, 8];

  protected readonly rooms = [
    { label: 'Summit / b-05', count: 42 },
    { label: 'Summit / b-06', count: 31 },
    { label: 'moonruins / a-09', count: 24 },
    { label: 'moonruins / a-11', count: 13 },
    { label: 'Core / c-03', count: 6 },
  ];

  protected readonly deaths = [
    { time: '21:48:07', mod: 'darkmoonruins', room: 'b-05', x: 7002, y: -2168 },
    { time: '21:47:51', mod: 'darkmoonruins', room: 'b-05', x: 6853, y: -2221 },
    { time: '21:46:12', mod: 'Celeste', room: '3', x: 142, y: 96 },
  ];

  protected readonly maxRoom = Math.max(...this.rooms.map((r) => r.count));

  protected readonly sprites: Sprite[] = [
    { file: 'madeline-idle', label: 'Madeline (repos)', scale: 6 },
    { file: 'madeline-dash', label: 'Madeline (dash)', scale: 6 },
    { file: 'madeline-death-1', label: 'Mort 1', scale: 6 },
    { file: 'madeline-death-2', label: 'Mort 2', scale: 6 },
    { file: 'badeline-idle', label: 'Badeline', scale: 6 },
    { file: 'strawberry-pixel', label: 'Fraise (pixel)', scale: 6 },
    { file: 'dash-crystal', label: 'Cristal de dash', scale: 6 },
    { file: 'strawberry', label: 'Fraise (interface)', scale: 1 },
    { file: 'goldberry', label: 'Fraise dorée', scale: 1 },
    { file: 'dashes', label: 'Dashes', scale: 1 },
    { file: 'area-city', label: 'Chapitre 1', scale: 0.6 },
    { file: 'area-resort', label: 'Chapitre 3', scale: 0.6 },
    { file: 'area-temple', label: 'Chapitre 5', scale: 0.6 },
    { file: 'area-core', label: 'Chapitre 8', scale: 0.6 },
    { file: 'logo', label: 'Logo', scale: 0.4 },
  ];

  protected demoLoading(): void {
    this.loading.set(true);
    setTimeout(() => this.loading.set(false), 1600);
  }

  protected onCode(event: Event): void {
    this.codeValue.set((event.target as HTMLInputElement).value.toUpperCase());
  }

  protected codeInvalid(): boolean {
    const v = this.codeValue().replace(/[-\s]/g, '');
    return v.length > 0 && v.length < 8;
  }

  protected onSpriteError(): void {
    this.spritesMissing.set(true);
  }
}
