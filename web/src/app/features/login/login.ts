import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /**
   * Page où retourner après connexion. On n'accepte qu'un chemin interne ("/pair") :
   * un returnUrl vers un autre site serait une redirection ouverte (phishing).
   */
  private get returnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
    return url.startsWith('/') && !url.startsWith('//') ? url : '/';
  }

  protected readonly mode = signal<'login' | 'register'>('login');
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  // Formulaire réactif : la structure et les règles de validation sont déclarées en TypeScript
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected toggleMode(): void {
    this.mode.update((m) => (m === 'login' ? 'register' : 'login'));
    this.error.set(null);
  }

  protected submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);

    const request$ =
      this.mode() === 'login'
        ? this.auth.login(email, password)
        : this.auth.register(email, password);

    request$.subscribe({
      next: () => {
        if (this.mode() === 'register') {
          // Compte créé : on enchaîne avec la connexion
          this.auth.login(email, password).subscribe({
            next: () => this.router.navigateByUrl(this.returnUrl),
            error: (e) => this.fail(e),
          });
        } else {
          this.router.navigateByUrl(this.returnUrl);
        }
      },
      error: (e) => this.fail(e),
    });
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    const status = err instanceof HttpErrorResponse ? err.status : 0;
    this.error.set(
      status === 401 ? 'Email ou mot de passe incorrect.'
      : status === 409 ? 'Un compte existe déjà avec cet email.'
      : status === 400 ? 'Email invalide ou mot de passe trop court (8 caractères minimum).'
      : status === 403 ? 'Requête refusée (session expirée). Recharge la page.'
      : 'Impossible de joindre le serveur.',
    );
  }
}
