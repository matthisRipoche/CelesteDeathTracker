import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService, User } from '../../core/auth.service';
import { Shell } from './shell';

@Component({ imports: [Shell], template: '<app-shell><p class="content">Contenu</p></app-shell>' })
class Host {}

const user = (over: Partial<User> = {}): User => ({
  id: 1,
  email: 'joueur@celeste.fr',
  createdAt: '2026-10-01T10:00:00Z',
  discordUsername: null,
  discordAvatarUrl: null,
  ...over,
});

describe('Shell', () => {
  let http: HttpTestingController;

  function open(u: User | null) {
    TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    auth.loadCurrentUser().subscribe();
    http.expectOne('/api/auth/me').flush(u ?? '', u ? { status: 200, statusText: 'OK' } : { status: 401, statusText: 'Unauthorized' });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: { nativeElement: unknown }) => f.nativeElement as HTMLElement;

  it('propose les quatre entrées du site, sans Appareils ni Associer un jeu', () => {
    const f = open(user());
    const labels = [...el(f).querySelectorAll('.ds-sidebar__nav a')].map((a) => a.textContent?.trim());
    expect(labels).toEqual(['Tableau de bord', 'Maps', 'Historique', 'Direct']);
    expect(el(f).textContent).not.toContain('Associer un jeu');
  });

  it('projette le contenu de la page à côté de la navigation', () => {
    const f = open(user());
    expect(el(f).querySelector('.main .page .content')?.textContent).toBe('Contenu');
  });

  it("montre l'avatar Discord quand le compte en a un", () => {
    const f = open(user({ email: null, discordUsername: 'Madeline', discordAvatarUrl: 'https://cdn.discordapp.com/avatars/1/abc.png' }));
    const img = el(f).querySelector('.ds-sidebar__user img') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('https://cdn.discordapp.com/avatars/1/abc.png');
    expect(el(f).querySelector('.ds-sidebar__name')?.textContent).toContain('Madeline');
  });

  it("montre l'initiale du nom quand il n'y a pas d'avatar", () => {
    const f = open(user());
    expect(el(f).querySelector('.ds-sidebar__user img')).toBeNull();
    expect(el(f).querySelector('.ds-sidebar__user .initial')?.textContent?.trim()).toBe('J');
  });

  it("revient à l'initiale quand l'image ne se charge pas", () => {
    const f = open(user({ discordUsername: 'Madeline', discordAvatarUrl: 'https://cdn.discordapp.com/avatars/1/cassee.png' }));
    (el(f).querySelector('.ds-sidebar__user img') as HTMLImageElement).dispatchEvent(new Event('error'));
    f.detectChanges();
    expect(el(f).querySelector('.ds-sidebar__user img')).toBeNull();
    expect(el(f).querySelector('.ds-sidebar__user .initial')?.textContent?.trim()).toBe('M');
  });

  it('ouvre le menu du compte avec Compte, Appareils et Déconnexion', () => {
    const f = open(user());
    expect(el(f).querySelector('.ds-menu')).toBeNull();

    const button = el(f).querySelector('.ds-sidebar__user') as HTMLButtonElement;
    button.click();
    f.detectChanges();

    expect(button.getAttribute('aria-expanded')).toBe('true');
    const items = [...el(f).querySelectorAll('.ds-menu__item')].map((i) => i.textContent?.trim());
    expect(items).toEqual(['Compte', 'Appareils', 'Déconnexion']);
    expect((el(f).querySelector('.ds-menu a[href="/account/devices"]'))).not.toBeNull();
  });

  it('ferme le menu avec Échap et par un clic ailleurs', () => {
    const f = open(user());
    const button = el(f).querySelector('.ds-sidebar__user') as HTMLButtonElement;

    button.click();
    f.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    f.detectChanges();
    expect(el(f).querySelector('.ds-menu')).toBeNull();

    button.click();
    f.detectChanges();
    (el(f).querySelector('.page') as HTMLElement).click();
    f.detectChanges();
    expect(el(f).querySelector('.ds-menu')).toBeNull();
  });

  it('a un onglet Compte dans la barre du bas mobile', () => {
    const f = open(user());
    const account = el(f).querySelector('.mobile-tabbar a[href="/account"]') as HTMLAnchorElement;
    expect(account.textContent).toContain('Compte');
    expect(el(f).querySelectorAll('.mobile-tabbar a').length).toBe(5);
  });
});
