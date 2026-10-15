import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth.service';
import { Login } from './features/login/login';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App, Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('login page renders the title and refuses an empty form', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h1')?.textContent).toContain('Celeste Death Tracker');

    const button = el.querySelector('button[type=submit]') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.field-error').length).toBe(2);
  });

  it('auth service starts in unknown state', () => {
    const auth = TestBed.inject(AuthService);
    expect(auth.isKnown()).toBeFalse();
    expect(auth.isAuthenticated()).toBeFalse();
  });
});
