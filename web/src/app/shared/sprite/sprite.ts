import { Component, input, signal } from '@angular/core';

/**
 * Sprite du jeu, en pixel art net. Les assets sont © Extremely OK Games : ils ne vivent que dans
 * `public/local-assets/` (hors Git, hors build de production). Si le fichier est absent, le composant
 * disparaît sans laisser d'image cassée : l'interface reste complète sans eux.
 */
@Component({
  selector: 'app-sprite',
  template: `
    @if (!failed()) {
      <img
        class="ds-sprite"
        [src]="'local-assets/celeste/' + name() + '.png'"
        [style.width.px]="size()"
        [style.height.px]="size()"
        [style.object-fit]="'contain'"
        alt=""
        aria-hidden="true"
        (error)="failed.set(true)"
      />
    }
  `,
  styles: `
    :host { display: inline-flex; }
  `,
})
export class Sprite {
  /** Nom du fichier sans extension, ex. « madeline-death-1 » (voir local-assets/celeste/index.json). */
  readonly name = input.required<string>();
  readonly size = input(48);

  protected readonly failed = signal(false);
}
