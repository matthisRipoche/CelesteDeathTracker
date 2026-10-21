import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Avatar } from '../avatar/avatar';
import { LiveBanner } from '../live-banner/live-banner';

/**
 * Coquille des pages connectées : barre latérale à gauche sur bureau (marque, navigation, menu du compte),
 * barre du bas sur mobile, et bandeau « En jeu » quand une tentative est ouverte. Le contenu occupe toute la
 * largeur restante. Chaque page est projetée dans <ng-content />.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, Avatar, LiveBanner],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly user = this.auth.user;
  protected readonly name = computed(() => {
    const u = this.user();
    return u?.discordUsername ?? u?.email ?? 'Mon compte';
  });
  protected readonly menuOpen = signal(false);

  /** Les quatre entrées du site. Compte et Appareils sont sous le menu du compte. */
  protected readonly links = [
    { path: '/', label: 'Tableau de bord', short: 'Accueil', exact: true },
    { path: '/maps', label: 'Maps', short: 'Maps', exact: false },
    { path: '/attempts', label: 'Historique', short: 'Historique', exact: false },
    { path: '/live', label: 'Direct', short: 'Direct', exact: false },
  ];

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  /** Un clic hors du menu, ou Échap, le ferme. */
  @HostListener('document:click', ['$event'])
  protected closeOnOutsideClick(event: Event): void {
    const wrap = this.host.nativeElement.querySelector('.account');
    if (this.menuOpen() && wrap && !wrap.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    this.menuOpen.set(false);
  }

  protected logout(): void {
    this.menuOpen.set(false);
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
