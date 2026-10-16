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

  protected readonly night: Swatch[] = [
    { token: '--ds-night-950', hex: '#0e0c1d', note: 'fond' },
    { token: '--ds-night-900', hex: '#151229', note: 'surface' },
    { token: '--ds-night-800', hex: '#1d1938', note: 'surface relevée' },
    { token: '--ds-night-700', hex: '#2a2450' },
    { token: '--ds-night-600', hex: '#3b3470', note: 'bordure' },
  ];

  protected readonly lavender: Swatch[] = [
    { token: '--ds-lavender-200', hex: '#e2defa' },
    { token: '--ds-lavender-300', hex: '#c9c4ee' },
    { token: '--ds-lavender-400', hex: '#9d97d1', note: 'logo · texte secondaire' },
    { token: '--ds-lavender-500', hex: '#8479c5', note: 'logo' },
    { token: '--ds-lavender-700', hex: '#675c83', note: 'logo' },
  ];

  protected readonly accents: Swatch[] = [
    { token: '--ds-pink-400', hex: '#f8588c', note: 'dash · accent' },
    { token: '--ds-pink-500', hex: '#f73e7b' },
    { token: '--ds-orange-400', hex: '#f68134', note: 'dash' },
    { token: '--ds-strawberry-500', hex: '#ed4a44', note: 'fraise · danger' },
    { token: '--ds-strawberry-700', hex: '#a81824' },
    { token: '--ds-green-400', hex: '#93bd40', note: 'cristal · succès' },
    { token: '--ds-gold-400', hex: '#f2c230', note: 'fraise dorée · alerte' },
    { token: '--ds-ice-400', hex: '#66c4e1', note: 'givre · info' },
    { token: '--ds-ice-300', hex: '#a6dcee', note: 'focus' },
  ];

  protected readonly semantic: Swatch[] = [
    { token: '--color-bg', hex: 'night-950' },
    { token: '--color-surface', hex: 'night-900' },
    { token: '--color-surface-raised', hex: 'night-800' },
    { token: '--color-border', hex: 'night-600' },
    { token: '--color-text', hex: 'snow' },
    { token: '--color-text-muted', hex: 'lavender-400' },
    { token: '--color-accent', hex: 'pink-400' },
    { token: '--color-info', hex: 'ice-400' },
    { token: '--color-success', hex: 'green-400' },
    { token: '--color-warning', hex: 'gold-400' },
    { token: '--color-danger', hex: 'strawberry-500' },
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
