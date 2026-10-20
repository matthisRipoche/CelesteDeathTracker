import { Component, ElementRef, inject, viewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LiveBanner } from '../live-banner/live-banner';

/**
 * Coquille des pages connectées : bandeau du haut (marque, navigation, compte), bandeau « En jeu »
 * quand une tentative est ouverte, et barre du bas sur mobile. Le contenu de chaque page est projeté
 * dans <ng-content />.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, LiveBanner],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private readonly content = viewChild.required<ElementRef<HTMLElement>>('content');

  protected readonly user = this.auth.user;

  protected readonly links = [
    { path: '/', label: 'Tableau de bord', exact: true },
    { path: '/maps', label: 'Maps', exact: false },
    { path: '/live', label: 'Direct', exact: false },
    { path: '/tokens', label: 'Appareils', exact: false },
    { path: '/pair', label: 'Associer un jeu', exact: false },
  ];

  /** Lien « Aller au contenu » : place le focus sur la zone principale de la page. */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.content().nativeElement.focus();
  }

  protected logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
