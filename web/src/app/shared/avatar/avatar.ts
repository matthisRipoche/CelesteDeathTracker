import { Component, computed, input, signal } from '@angular/core';

/**
 * Photo de profil : l'avatar Discord quand le compte en a un, sinon la première lettre du nom sur un fond
 * uni (aussi quand l'image ne se charge pas). Décorative : le nom est toujours écrit à côté ou dans le
 * libellé du bouton qui la contient.
 */
@Component({
  selector: 'app-avatar',
  template: `
    @if (url() && !broken()) {
      <img [src]="url()" alt="" [width]="size()" [height]="size()" referrerpolicy="no-referrer" (error)="broken.set(true)" />
    } @else {
      <span class="initial" aria-hidden="true">{{ initial() }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-grid; place-items: center; flex: none; overflow: hidden;
      width: calc(var(--avatar-size, 40) * 1px); height: calc(var(--avatar-size, 40) * 1px);
      background: var(--color-highlight); color: var(--color-on-highlight);
      border-radius: var(--radius-md);
      font-family: var(--font-display); font-weight: 800;
      font-size: calc(var(--avatar-size, 40) * 0.5px);
    }
    img { display: block; width: 100%; height: 100%; object-fit: cover; }
  `,
  host: { '[style.--avatar-size]': 'size()' },
})
export class Avatar {
  /** Adresse de l'image (celle de Discord), ou vide. */
  readonly url = input<string | null | undefined>(null);
  /** Nom affiché à côté : sa première lettre sert de repli. */
  readonly name = input<string | null | undefined>(null);
  /** Côté en pixels. */
  readonly size = input(40);

  protected readonly broken = signal(false);
  protected readonly initial = computed(() => (this.name() ?? '?').trim().charAt(0).toUpperCase() || '?');
}
