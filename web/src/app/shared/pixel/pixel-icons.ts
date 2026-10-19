import { Component, input } from '@angular/core';

/**
 * Motifs pixel dessinés pour le site (pas des sprites du jeu : ceux-ci sont © Extremely OK Games).
 * Les couleurs viennent des classes .ds-px-* de ds.css, donc elles suivent le thème clair ou sombre.
 */

/** Fraise dorée : un deathless réussi (dans le jeu, elle se gagne en finissant un chapitre sans mourir). */
@Component({
  selector: 'app-px-berry',
  template: `
    <svg [attr.width]="size() * 0.8" [attr.height]="size()" viewBox="0 0 8 10" shape-rendering="crispEdges" aria-hidden="true">
      <path class="ds-px-leaf" d="M3 0h2v1H3ZM6 0h1v1H6ZM2 1h6v1H2ZM4 2h2v1H4Z" />
      <path class="ds-px-berry" d="M1 3h7v1H1ZM0 4h8v1H0ZM0 5h8v1H0ZM0 6h8v1H0ZM1 7h7v1H1ZM2 8h5v1H2ZM3 9h3v1H3Z" />
      <path class="ds-px-berry-shade" d="M7 4h1v4H7ZM6 8h1v1H6ZM5 9h1v1H5Z" />
      <path class="ds-px-berry-light" d="M2 4h1v1H2ZM1 5h1v1H1Z" />
    </svg>
  `,
  styles: `:host { display: inline-flex; }`,
})
export class PxBerry {
  readonly size = input(20);
}

/** Explosion de pixels : une tentative qui se termine par une mort. */
@Component({
  selector: 'app-px-burst',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 7 7" shape-rendering="crispEdges" aria-hidden="true">
      <path class="ds-px-fall" d="M3 0h1v1H3ZM5 1h1v1H5ZM6 3h1v1H6ZM5 5h1v1H5ZM3 6h1v1H3ZM1 5h1v1H1ZM0 3h1v1H0ZM1 1h1v1H1Z" />
    </svg>
  `,
  styles: `:host { display: inline-flex; }`,
})
export class PxBurst {
  readonly size = input(18);
}
