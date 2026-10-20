import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

const SITE = 'Celeste Death Tracker';

/**
 * Titre de l'onglet : « <titre de la route> · Celeste Death Tracker ». C'est aussi la première
 * chose qu'annonce un lecteur d'écran à chaque changement de page (WCAG 2.4.2).
 */
@Injectable({ providedIn: 'root' })
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const page = this.buildTitle(snapshot);
    this.title.setTitle(page ? `${page} · ${SITE}` : SITE);
  }
}
